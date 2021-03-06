package com.csswust.patest2.service.judge;

import com.alibaba.fastjson.JSON;
import com.csswust.patest2.common.config.Config;
import com.csswust.patest2.common.config.SiteKey;
import com.csswust.patest2.dao.*;
import com.csswust.patest2.dao.common.BaseQuery;
import com.csswust.patest2.entity.*;
import com.csswust.patest2.service.JudgeService;
import com.csswust.patest2.service.common.BaseService;
import com.csswust.patest2.utils.ArrayUtil;
import com.csswust.patest2.utils.FileUtil;
import com.csswust.patest2.utils.StreamUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author 杨顺丰
 */
@Service
public class JudgeServiceImpl extends BaseService implements JudgeService {
    private static Logger log = LoggerFactory.getLogger(JudgeServiceImpl.class);

    @Autowired
    private SubmitInfoDao submitInfoDao;
    @Autowired
    private JudgerInfoDao judgerInfoDao;
    @Autowired
    private ProblemInfoDao problemInfoDao;
    @Autowired
    private SubmitResultDao submitResultDao;
    @Autowired
    private ExamPaperDao examPaperDao;
    @Autowired
    private PaperProblemDao paperProblemDao;
    @Autowired
    private ExamParamDao examParamDao;
    @Autowired
    private ExamInfoDao examInfoDao;

    @Override
    public JudgeTask getTaskBySubmId(Integer submId) {
        SubmitInfo submitInfo = submitInfoDao.selectByPrimaryKey(submId);
        if (submitInfo != null) {
            Integer probId = submitInfo.getProblemId();
            JudgerInfo judgerInfo = judgerInfoDao.selectByPrimaryKey(submitInfo.getJudgerId());
            if (judgerInfo == null) {
                return null;
            }
            ProblemInfo problemInfo = problemInfoDao.selectByPrimaryKey(probId);
            if (problemInfo == null) {
                return null;
            }
            String source = submitInfo.getSource();
            Integer timeLimit = problemInfo.getTimeLimit();
            Integer memoryLimit = problemInfo.getMemoryLimit();
            String language = judgerInfo.getName();
            Integer judgeModel = problemInfo.getJudgeModel();
            Integer testdataNum = problemInfo.getTestdataNum();
            return new JudgeTask(submId, memoryLimit, timeLimit, probId,
                    testdataNum, judgerInfo.getJudId(), source, judgeModel);
        }
        return null;
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    @Override
    public Map<String, Object> refresh(JudgeTask judgeTask, JudgeResult judgeResult) {
        Integer submId = judgeTask.getSubmId();
        if (judgeResult.getConsoleMsg() == null) judgeResult.setConsoleMsg("");
        if (judgeResult.getErrMsg() == null) judgeResult.setErrMsg("");

        List<SubmitResult> submitResultList = null;
        try {
            // 转化执行数据
            Gson gson = new Gson();
            submitResultList = gson.fromJson(judgeResult.getConsoleMsg(), new TypeToken<List<SubmitResult>>() {
            }.getType());
            if (submitResultList == null || submitResultList.size() == 0) {
                throw new RuntimeException();
            }
        } catch (Exception e) {
            log.error("gson.fromJson data: {} error: {}", getJson(judgeResult), e);
            submitResultList = new ArrayList<>();
            // 如果转化失败，那么就是发生了位置错误
            SubmitResult submitResult = new SubmitResult(
                    judgeTask.getSubmId(), 0, 10,
                    -1, -1,
                    judgeResult.getErrMsg());
            submitResultList.add(submitResult);
        }
        SubmitInfo submitInfo = submitInfoDao.selectByPrimaryKey(submId);
        if (submitInfo == null) return null;
        // 删除原来已经有的，目的是为了重判
        submitResultDao.deleteBySubmId(submId);
        // 插入并计算总的判题结果,
        calculationResult(submId, submitResultList);
        // 当提交不是老师测试时，那么需要更新paperProblem和ExamPaper
        if (submitInfo.getIsTeacherTest() == 0) {
            // 需要重新查询一次，应为calculationResult会更新最新结果
            submitInfo = submitInfoDao.selectByPrimaryKey(submId);
            PaperProblem paperProblem = paperProblemDao.selectByPrimaryKey(submitInfo.getPaperProblemId());
            if (paperProblem == null) return null;
            // 给考生的试题打分，也就是更新paperProblem
            grade(submitInfo, paperProblem, submitResultList);
            // 刷新考卷
            refreshPaperById(submitInfo.getPaperProblemId());
        }
        return null;
    }

    /**
     * 通过一个非测试的提交给考生打分
     */
    private void grade(SubmitInfo submitInfo, PaperProblem paperProblem, List<SubmitResult> submitResultList) {
        ProblemInfo problemInfo = problemInfoDao.selectByPrimaryKey(submitInfo.getProblemId());
        if (paperProblem == null || problemInfo == null) {
            return;
        }
        ExamParam examParam = examParamDao.selectByPrimaryKey(paperProblem.getExamParamId());
        if (examParam == null) {
            return;
        }
        ExamInfo examInfo = examInfoDao.selectByPrimaryKey(examParam.getExamId());
        if (examInfo == null) {
            return;
        }
        int allScore = examParam.getScore();
        List<Integer> ScoreRatioList = null;
        try {
            ScoreRatioList = ArrayUtil.StringToArray(problemInfo.getScoreRatio(), ",");
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (paperProblem.getIsAced() != 1) {
            paperProblem.setSubmitId(submitInfo.getSubmId());
            if (submitInfo.getStatus() != 1) {
                paperProblem.setIsAced(submitInfo.getStatus());
            } else if (submitInfo.getStatus() == 1) {
                paperProblem.setIsAced(1);
                int interval = (int) (submitInfo.getCreateTime().getTime()
                        - examInfo.getStartTime().getTime()) / 1000;
                paperProblem.setUsedTime(interval + paperProblem.getSubmitCount() * 20 * 60);
            }
        }
        double ans = 0;
        if (submitResultList != null && submitResultList.size() != 0) {
            int sum = 0;
            for (int i = 0; i < submitResultList.size(); i++) {
                Integer testId = submitResultList.get(i).getTestId();
                if (submitResultList.get(i).getStatus() == 1 && testId != null && testId >= 0
                        && testId < ScoreRatioList.size()) {
                    sum = sum + ScoreRatioList.get(testId);
                }
            }
            ans = (sum * 1.0 / 100) * allScore;
        }
        if (ans > paperProblem.getScore()) {
            paperProblem.setScore((int) ans);
        }
        paperProblem.setLastSubmitTime(submitInfo.getCreateTime());
        paperProblem.setSubmitCount(paperProblem.getSubmitCount() + 1);
        paperProblemDao.updateByPrimaryKeySelective(paperProblem);
    }

    /**
     * 判题完成后，把总的结果计算出来保存到提示信息里面
     */
    private void calculationResult(Integer submId, List<SubmitResult> submitResultList) {
        SubmitInfo submitInfo = new SubmitInfo();
        submitInfo.setSubmId(submId);
        // 结果初始化为Oops. Waiting
        int lastStatus = 11, usedTime = -1, usedMemory = -1;
        for (int i = 0; i < submitResultList.size(); i++) {
            // 插入SubmitResult
            SubmitResult submitResult = submitResultList.get(i);
            submitResult.setSubmitId(submId);
            submitResultDao.insertSelective(submitResult);
            // 如果AC，则统计最大usedTime和usedMemory
            if (submitResult.getStatus() == 1) {
                // 如果是最后一个AC，那么记录status为AC
                if (lastStatus == 11 && i == submitResultList.size() - 1) {
                    lastStatus = 1;
                }
                usedTime = Math.max(usedTime, submitResult.getUsedTime());
                usedMemory = Math.max(usedMemory, submitResult.getUsedMemory());
            } else {
                // 遇到第一个非AC
                if (lastStatus == 11) {
                    lastStatus = submitResult.getStatus();
                }
            }
            if (!submitResult.getErrMsg().equals("")) {
                submitInfo.setErrMsg(submitResult.getErrMsg());
            }
        }
        submitInfo.setStatus(lastStatus);
        submitInfo.setUsedTime(usedTime);
        submitInfo.setUsedMemory(usedMemory);
        submitInfoDao.updateByPrimaryKeySelective(submitInfo);
    }

    /**
     * 通过考卷刷新考生的成绩
     */
    @Override
    public void refreshPaperById(Integer papProId) {
        PaperProblem paperProblem = paperProblemDao.selectByPrimaryKey(papProId);
        if (paperProblem == null) return;
        ExamPaper examPaper = examPaperDao.selectByPrimaryKey(paperProblem.getExamPaperId());
        if (examPaper == null) return;
        PaperProblem record = new PaperProblem();
        record.setExamPaperId(examPaper.getExaPapId());
        List<PaperProblem> paperProblemList = paperProblemDao.selectByCondition(record, new BaseQuery());
        int acSum = 0, score = 0, usedTime = 0;
        for (int i = 0; i < paperProblemList.size(); i++) {
            if (paperProblemList.get(i).getIsAced() == 1) {
                acSum++;
            }
            score += paperProblemList.get(i).getScore();
            usedTime += paperProblemList.get(i).getUsedTime();
        }
        examPaper.setScore(score);
        examPaper.setAcedCount(acSum);
        examPaper.setUsedTime(usedTime);
        examPaperDao.updateByPrimaryKeySelective(examPaper);
    }

    @Override
    public JudgeResult judge(JudgeTask judgeTask) {
        JudgeResult judgeResult = new JudgeResult();
        String fileName = null;
        String sourcepath = null;
        try {
            // 获得判题源文件的文件名
            JudgerInfo judgerInfo = judgerInfoDao.selectByPrimaryKey(judgeTask.getLanguage());
            fileName = getFileName(judgeTask.getLanguage());
            sourcepath = Config.get(SiteKey.JUDGE_SOURCE_PATH);
            String scriptPath = Config.get(SiteKey.JUDGE_SCRIPT_PATH) + File.separator;
            String scriptName = Config.get(SiteKey.JUDGE_SCRIPT_NAME);
            // 创建源文件并写入代码 =
            FileUtil.generateFile(judgeTask.getSource(), sourcepath, fileName);
            // 构建命令行命令
            StringBuilder cmd = new StringBuilder();
            cmd.append("python").append(" ").append(scriptPath).append(scriptName)
                    .append(" ").append(judgeTask.getPid()).append(" ")
                    .append(judgeTask.getTestdataNum()).append(" ").append(judgerInfo.getName())
                    .append(" ").append(judgeTask.getLimitTime()).append(" ")
                    .append(judgeTask.getLimitMemory()).append(" ")
                    .append(judgeTask.getJudgeMode());
            // 执行
            Runtime rt = Runtime.getRuntime();
            Process proc = rt.exec(cmd.toString());
            // 获得错误信息
            String errMsg = StreamUtil.output(proc.getErrorStream());
            // 获得控制台信息
            String consoleMsg = StreamUtil.output(proc.getInputStream());
            System.out.println(judgeTask + "\n" + errMsg + "\n" + consoleMsg);
            judgeResult.setErrMsg(errMsg);
            judgeResult.setConsoleMsg(consoleMsg);
            log.error("judge \n consoleMsg :{} \n info :{}", consoleMsg, JSON.toJSONString(judgeTask));
        } catch (IOException e) {
            log.error("judge error data :{} error: {}", JSON.toJSONString(judgeResult), e);
        } finally {
            // 删除源文件，如果没有执行将会导致判题系统堵塞
            FileUtil.removeFile(sourcepath, fileName);
        }
        return judgeResult;
    }

    private String getFileName(Integer language) {
        String[] strings = new String[]{
                SiteKey.JUDGE_GCC_FILE_NAME,
                SiteKey.JUDGE_GPP_FILE_NAME,
                SiteKey.JUDGE_JAVA_FILE_NAME,
                SiteKey.JUDGE_PYTHON_FILE_NAME
        };
        return Config.get(strings[language - 1]);
    }
}
