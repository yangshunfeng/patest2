package com.csswust.patest2.service;

import com.csswust.patest2.service.result.DrawProblemRe;
import com.csswust.patest2.service.result.ExamPaperLoadRe;
import org.springframework.web.multipart.MultipartFile;

/**
 * Created by 972536780 on 2018/3/21.
 */
public interface ExamPaperService {
    ExamPaperLoadRe insertByExcel(MultipartFile file, Integer examId, boolean isIgnoreError);

    DrawProblemRe drawProblemByExamId(Integer examId);
}
