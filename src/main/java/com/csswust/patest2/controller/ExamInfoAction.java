package com.csswust.patest2.controller;

import com.csswust.patest2.controller.common.BaseAction;
import com.csswust.patest2.dao.*;
import com.csswust.patest2.dao.common.BaseDao;
import com.csswust.patest2.dao.common.BaseQuery;
import com.csswust.patest2.entity.*;
import com.csswust.patest2.service.ExamInfoService;
import com.csswust.patest2.service.result.ImportDataRe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

import static com.csswust.patest2.service.common.BatchQueryService.getFieldByList;
import static com.csswust.patest2.service.common.BatchQueryService.selectRecordByIds;

/**
 * Created by 972536780 on 2018/3/19.
 */
@RestController
@RequestMapping("/examInfo")
public class ExamInfoAction extends BaseAction {
    private static Logger log = LoggerFactory.getLogger(ExamInfoAction.class);

    @Autowired
    private ExamInfoDao examInfoDao;
    @Autowired
    private ExamPaperDao examPaperDao;
    @Autowired
    private PaperProblemDao paperProblemDao;
    @Autowired
    private ProblemInfoDao problemInfoDao;
    @Autowired
    private KnowledgeInfoDao knowledgeInfoDao;
    @Autowired
    private CourseInfoDao courseInfoDao;
    @Autowired
    private UserInfoDao userInfoDao;
    @Autowired
    private UserProfileDao userProfileDao;
    @Autowired
    private ExamInfoService examInfoService;

    @RequestMapping(value = "/selectByCondition", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> selectTempProblem(
            ExamInfo examInfo,
            @RequestParam(required = false, defaultValue = "false") Boolean isRecent,
            @RequestParam(required = false, defaultValue = "false") Boolean onlyExamInfo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer rows) {
        Map<String, Object> res = new HashMap<>();
        BaseQuery baseQuery = new BaseQuery();
        if (examInfo.getStartTime() != null) {
            baseQuery.setCustom("startTime", examInfo.getStartTime());
            examInfo.setStartTime(null);
        }
        if (examInfo.getEndTime() != null) {
            baseQuery.setCustom("endTime", examInfo.getEndTime());
            examInfo.setEndTime(null);
        }
        if (isRecent) {
            baseQuery.setCustom("isRecent", true);
        }
        Integer total = examInfoDao.selectByConditionGetCount(examInfo, baseQuery);
        baseQuery.setPageRows(page, rows);
        List<ExamInfo> examInfoList = examInfoDao.selectByCondition(examInfo, baseQuery);
        res.put("total", total);
        res.put("examInfoList", examInfoList);
        if (onlyExamInfo) {
            return res;
        }
        List<Integer> peopleTotal = new ArrayList<>();
        List<Integer> statusList = new ArrayList<Integer>();
        List<Integer> proState = new ArrayList<Integer>();
        ExamPaper examPaper = new ExamPaper();
        Date time = new Date();
        for (int i = 0; i < examInfoList.size(); i++) {
            ExamInfo item = examInfoList.get(i);
            examPaper.setExamId(item.getExamId());
            int temp = examPaperDao.selectByConditionGetCount(examPaper, new BaseQuery());
            peopleTotal.add(temp);
            Integer status;
            if (time.getTime() > item.getEndTime().getTime()) {
                status = 2;
            } else if (time.getTime() > item.getStartTime().getTime()) {
                status = 1;
            } else {
                status = 0;
            }
            statusList.add(status);
            PaperProblem paperProblem = new PaperProblem();
            paperProblem.setExamId(item.getExamId());
            int size = paperProblemDao.selectByConditionGetCount(paperProblem, new BaseQuery());
            proState.add(size == 0 ? 0 : 1);
        }

        res.put("peopleTotal", peopleTotal);
        res.put("statusList", statusList);
        res.put("proState", proState);
        return res;
    }

    @RequestMapping(value = "/insertOne", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> insertOne(ExamInfo examInfo) {
        Map<String, Object> res = new HashMap<>();
        int result = examInfoDao.insertSelective(examInfo);
        res.put("status", result);
        res.put("examId", examInfo.getExamId());
        return res;
    }

    @RequestMapping(value = "/updateById", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> updateById(ExamInfo examInfo) {
        Map<String, Object> res = new HashMap<>();
        int result = examInfoDao.updateByPrimaryKeySelective(examInfo);
        res.put("status", result);
        return res;
    }

    @RequestMapping(value = "/deleteByIds", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> deleteByIds(@RequestParam(required = true) String ids) {
        Map<String, Object> res = new HashMap<>();
        int result = examInfoDao.deleteByIds(ids);
        res.put("status", result);
        return res;
    }

    @RequestMapping(value = "/selectMyProblem", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> selectMyProblem(
            @RequestParam(required = true) Integer examId) {
        Map<String, Object> res = new HashMap<>();
        ExamPaper examPaper = new ExamPaper();
        examPaper.setExamId(examId);
        List<ExamPaper> examPaperList = examPaperDao.selectByCondition(examPaper, new BaseQuery());
        if (examPaperList == null) {
            examPaperList = new ArrayList<>();
        }
        List<Integer> probIdList = new ArrayList<>();
        List<Integer> countList = new ArrayList<>();
        for (int i = 0; i < examPaperList.size(); i++) {
            PaperProblem record = new PaperProblem();
            record.setExamPaperId(examPaperList.get(i).getExaPapId());
            List<PaperProblem> paperProblemList = paperProblemDao.selectByCondition(record, new BaseQuery());
            for (int j = 0; j < paperProblemList.size(); j++) {
                Integer probId = paperProblemList.get(j).getProblemId();
                if (probIdList.contains(probId)) {
                    int index = probIdList.indexOf(probId);
                    countList.set(index, countList.get(index) + 1);
                } else {
                    probIdList.add(probId);
                    countList.add(1);
                }
            }
        }
        List<ProblemInfo> problemInfoList = selectRecordByIds(probIdList,
                "probId", (BaseDao) problemInfoDao, ProblemInfo.class);
        List<KnowledgeInfo> knowledgeInfoList = selectRecordByIds(
                getFieldByList(problemInfoList, "knowId", ProblemInfo.class),
                "knowId", (BaseDao) knowledgeInfoDao, KnowledgeInfo.class);
        List<CourseInfo> courseInfoList = selectRecordByIds(
                getFieldByList(knowledgeInfoList, "courseId", KnowledgeInfo.class),
                "couId", (BaseDao) courseInfoDao, CourseInfo.class);
        res.put("probIdList", probIdList);
        res.put("countList", countList);
        res.put("problemInfoList", problemInfoList);
        res.put("knowledgeInfoList", knowledgeInfoList);
        res.put("courseInfoList", courseInfoList);
        return res;
    }

    @RequestMapping(value = "/rankingByGrade", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> rankingByGrade(
            @RequestParam(required = true) Integer examId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer rows) {
        Map<String, Object> res = new HashMap<>();
        ExamPaper examPaper = new ExamPaper();
        examPaper.setExamId(examId);
        BaseQuery baseQuery = new BaseQuery();
        Integer total = examPaperDao.selectByConditionGetCount(examPaper, baseQuery);
        baseQuery.setPageRows(page, rows);
        baseQuery.setCustom("sort", "sort");
        List<ExamPaper> allExamPaperList = examPaperDao.selectByCondition(examPaper, baseQuery);
        List<UserInfo> userInfoList = selectRecordByIds(
                getFieldByList(allExamPaperList, "userId", ExamPaper.class),
                "userId", (BaseDao) userInfoDao, UserInfo.class);
        List<UserProfile> userProfileList = selectRecordByIds(
                getFieldByList(userInfoList, "userProfileId", UserInfo.class),
                "useProId", (BaseDao) userProfileDao, UserProfile.class);
        List<List<PaperProblem>> PaperProblemList = new ArrayList<>();
        List<List<ProblemInfo>> ProblemInfoList = new ArrayList<>();
        PaperProblem paperProblem = new PaperProblem();
        int problemTotal = 0;
        for (ExamPaper item : allExamPaperList) {
            paperProblem.setExamPaperId(item.getExaPapId());
            List<PaperProblem> paperProblems = paperProblemDao.selectByCondition(paperProblem, new BaseQuery());
            List<ProblemInfo> problemInfos = selectRecordByIds(
                    getFieldByList(paperProblems, "problemId", PaperProblem.class),
                    "probId", (BaseDao) problemInfoDao, ProblemInfo.class);
            if (paperProblems.size() > problemTotal) {
                problemTotal = paperProblems.size();
            }
            PaperProblemList.add(paperProblems);
            ProblemInfoList.add(problemInfos);
        }
        res.put("total", total);
        res.put("examPaperList", allExamPaperList);
        res.put("PaperProblemList", PaperProblemList);
        res.put("ProblemInfoList", ProblemInfoList);
        res.put("userInfoList", userInfoList);
        res.put("userProfileList", userProfileList);
        res.put("problemTotal", problemTotal);
        return res;
    }

    @RequestMapping(value = "/importCodeByExamId", method = {RequestMethod.GET, RequestMethod.POST})
    public ImportDataRe importCodeByExamId(@RequestParam(required = true) Integer examId) {
        return examInfoService.importCodeByExamId(examId);
    }

    @RequestMapping(value = "/importGradeByExamId", method = {RequestMethod.GET, RequestMethod.POST})
    public ImportDataRe importGradeByExamId(@RequestParam(required = true) Integer examId) {
        return examInfoService.importGradeByExamId(examId);
    }
}
