package com.csswust.patest2.controller;

import com.csswust.patest2.controller.common.BaseAction;
import com.csswust.patest2.dao.SubmitInfoDao;
import com.csswust.patest2.dao.SubmitSimilarityDao;
import com.csswust.patest2.dao.UserInfoDao;
import com.csswust.patest2.dao.UserProfileDao;
import com.csswust.patest2.dao.common.BaseDao;
import com.csswust.patest2.dao.common.BaseQuery;
import com.csswust.patest2.entity.SubmitInfo;
import com.csswust.patest2.entity.SubmitSimilarity;
import com.csswust.patest2.entity.UserInfo;
import com.csswust.patest2.entity.UserProfile;
import com.csswust.patest2.utils.SimHash;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.csswust.patest2.service.common.BatchQueryService.getFieldByList;
import static com.csswust.patest2.service.common.BatchQueryService.selectRecordByIds;

/**
 * Created by 972536780 on 2018/3/24.
 */
@Validated
@RestController
@RequestMapping("/submitSimilarity")
public class SubmitSimilarityAction extends BaseAction {
    private static Logger log = LoggerFactory.getLogger(SubmitSimilarityAction.class);

    @Autowired
    private SubmitSimilarityDao submitSimilarityDao;
    @Autowired
    private UserInfoDao userInfoDao;
    @Autowired
    private SubmitInfoDao submitInfoDao;
    @Autowired
    private UserProfileDao userProfileDao;

    @RequestMapping(value = "/selectByCondition", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> selectByCondition(
            SubmitSimilarity submitSimilarity,
            @RequestParam(required = false) Double lowerLimit,
            @RequestParam(required = false) Double upperLimit,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer rows) {
        Map<String, Object> res = new HashMap<>();
        BaseQuery baseQuery = new BaseQuery();
        baseQuery.setCustom("lowerLimit", lowerLimit);
        baseQuery.setCustom("upperLimit", upperLimit);
        if (StringUtils.isNotBlank(userName)) {
            UserInfo userInfo = userInfoDao.selectByUsername(userName);
            if (userInfo != null) {
                SubmitInfo submitInfo = new SubmitInfo();
                submitInfo.setUserId(userInfo.getUserId());
                List<SubmitInfo> submitInfoList = submitInfoDao.selectByCondition(submitInfo, new BaseQuery());
                List<Integer> submId = getFieldByList(submitInfoList, "submId", SubmitInfo.class);
                baseQuery.setCustom("submitIds1", submId);
            }
        }
        Integer total = submitSimilarityDao.selectByConditionGetCount(submitSimilarity, baseQuery);
        baseQuery.setPageRows(page, rows);
        List<SubmitSimilarity> submitSimilarityList = submitSimilarityDao.selectByCondition(submitSimilarity, baseQuery);
        List<SubmitInfo> submitInfoList1 = selectRecordByIds(
                getFieldByList(submitSimilarityList, "submitId1", SubmitSimilarity.class),
                "submId", (BaseDao) submitInfoDao, SubmitInfo.class);
        List<UserInfo> userInfoList1 = selectRecordByIds(
                getFieldByList(submitInfoList1, "userId", SubmitInfo.class),
                "userId", (BaseDao) userInfoDao, UserInfo.class);
        List<UserProfile> userProfileList1 = selectRecordByIds(
                getFieldByList(userInfoList1, "userProfileId", UserInfo.class),
                "useProId", (BaseDao) userProfileDao, UserProfile.class);

        List<SubmitInfo> submitInfoList2 = selectRecordByIds(
                getFieldByList(submitSimilarityList, "submitId2", SubmitSimilarity.class),
                "submId", (BaseDao) submitInfoDao, SubmitInfo.class);
        List<UserInfo> userInfoList2 = selectRecordByIds(
                getFieldByList(submitInfoList2, "userId", SubmitInfo.class),
                "userId", (BaseDao) userInfoDao, UserInfo.class);
        List<UserProfile> userProfileList2 = selectRecordByIds(
                getFieldByList(userInfoList2, "userProfileId", UserInfo.class),
                "useProId", (BaseDao) userProfileDao, UserProfile.class);
        res.put("total", total);
        res.put("submitSimilarityList", submitSimilarityList);
        res.put("submitInfoList1", submitInfoList1);
        res.put("submitInfoList2", submitInfoList2);
        res.put("userInfoList1", userInfoList1);
        res.put("userProfileList1", userProfileList1);
        res.put("userInfoList2", userInfoList2);
        res.put("userProfileList2", userProfileList2);
        return res;
    }

    private Double getSimilarity(String submSource, String submSource2) {
        SimHash hash1 = new SimHash(submSource, 128);
        SimHash hash2 = new SimHash(submSource2, 128);
        return (double) (128 - hash1.hammingDistance(hash2)) / 128;
    }

    @RequestMapping(value = "/getSimByProbId", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> getSimByProbId(
            @RequestParam(required = true) Integer examId,
            @RequestParam(required = true) Integer probId) {
        Map<String, Object> res = new HashMap<>();
        if (examId == null || probId == null) {
            return res;
        }
        SubmitInfo submitInfo = new SubmitInfo();
        submitInfo.setExamId(examId);
        submitInfo.setProblemId(probId);
        List<SubmitInfo> submitInfoList = submitInfoDao.selectByCondition(submitInfo, new BaseQuery());
        if (submitInfoList == null || submitInfoList.size() == 0) {
            res.put("status", -1);
            res.put("desc", "本题无提交数据");
        }
        SubmitSimilarity temp = new SubmitSimilarity();
        temp.setSubmitId1(submitInfoList.get(0).getSubmId());
        List<SubmitSimilarity> templist = submitSimilarityDao.selectByCondition(temp, new BaseQuery(1, 1));
        if (templist.size() > 0) {
            res.put("status", -2);
            res.put("desc", "不能重复计算相似度");
            return res;
        }
        int count = 0;
        int lenth = submitInfoList.size();
        for (int i = 0; i < lenth; i++) {
            for (int j = 0; j < lenth; j++) {
                if (i == j) continue;
                SubmitInfo subInfo1, subInfo2;
                subInfo1 = submitInfoList.get(i);
                subInfo2 = submitInfoList.get(j);
                if (subInfo1.getUserId().intValue() == subInfo2.getUserId().intValue()) continue;
                SubmitSimilarity submitSimilarity = new SubmitSimilarity();
                submitSimilarity.setSubmitId1(subInfo1.getSubmId());
                submitSimilarity.setExamId(examId);
                submitSimilarity.setSubmitId2(subInfo2.getSubmId());
                submitSimilarity.setSimilarity(getSimilarity(subInfo1.getSource(), subInfo2.getSource()));
                count += submitSimilarityDao.insertSelective(submitSimilarity);
            }
        }
        res.put("status", count);
        return res;
    }
}
