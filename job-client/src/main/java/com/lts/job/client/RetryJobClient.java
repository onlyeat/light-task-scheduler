package com.lts.job.client;

import com.lts.job.client.domain.JobClientNode;
import com.lts.job.client.domain.ResponseCode;
import com.lts.job.core.domain.Job;
import com.lts.job.client.domain.Response;
import com.lts.job.core.file.FileException;
import com.lts.job.core.file.Line;
import com.lts.job.core.support.RetryScheduler;
import com.lts.job.core.util.JSONUtils;
import com.lts.job.core.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Robert HG (254963746@qq.com) on 8/14/14.
 *         重试 客户端, 如果 没有可用的JobTracker, 那么存文件, 定时重试
 */
public class RetryJobClient extends JobClient<JobClientNode> {

    private RetryScheduler retryScheduler;

    @Override
    protected void nodeStart() {

        retryScheduler = new RetryScheduler<Job>(30) {
            @Override
            protected boolean isRemotingEnable() {
                return isServerEnable();
            }

            @Override
            protected boolean retry(List<Job> jobs) {
                return superSubmitJob(jobs).isSuccess();
            }
        };
        super.nodeStart();
        retryScheduler.start();
    }

    @Override
    protected void nodeStop() {
        super.nodeStop();
        retryScheduler.stop();
    }

    @Override
    public Response submitJob(Job job) {
        return submitJob(Arrays.asList(job));
    }

    @Override
    public Response submitJob(List<Job> jobs) {
        Response response = superSubmitJob(jobs);

        if (!response.isSuccess()) {
            try {
                for (Job job : response.getFailedJobs()) {
                    retryScheduler.inSchedule(job.getTaskId(), job);
                }
                response.setSuccess(true);
                response.setCode(ResponseCode.FAILED_AND_SAVE_FILE);
            } catch (Exception e) {
                response.setSuccess(false);
                response.setMsg(e.getMessage());
            }
        }

        return response;
    }

    private Response superSubmitJob(List<Job> jobs) {
        return super.submitJob(jobs);
    }
}
