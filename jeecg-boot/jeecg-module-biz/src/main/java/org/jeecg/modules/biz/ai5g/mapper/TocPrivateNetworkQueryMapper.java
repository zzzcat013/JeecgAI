package org.jeecg.modules.biz.ai5g.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** ToC 随行专网只读查询。 */
@Mapper
public interface TocPrivateNetworkQueryMapper {
    List<Map<String, Object>> selectSummary();
    List<Map<String, Object>> selectProjects(@Param("keyword") String keyword,
                                             @Param("status") String status);
    long countResources(@Param("projectCode") String projectCode, @Param("keyword") String keyword);
    List<Map<String, Object>> selectResources(@Param("projectCode") String projectCode,
                                              @Param("keyword") String keyword,
                                              @Param("offset") long offset, @Param("pageSize") long pageSize);
    long countRoutes(@Param("projectCode") String projectCode, @Param("keyword") String keyword);
    List<Map<String, Object>> selectRoutes(@Param("projectCode") String projectCode,
                                           @Param("keyword") String keyword,
                                           @Param("offset") long offset, @Param("pageSize") long pageSize);
    long countDocuments(@Param("projectCode") String projectCode, @Param("keyword") String keyword);
    List<Map<String, Object>> selectDocuments(@Param("projectCode") String projectCode,
                                              @Param("keyword") String keyword,
                                              @Param("offset") long offset, @Param("pageSize") long pageSize);
    List<Map<String, Object>> selectGuides();
    int updateProject(@Param("projectCode") String projectCode,
                      @Param("projectStatus") String projectStatus,
                      @Param("bandwidth") String bandwidth,
                      @Param("dnn") String dnn,
                      @Param("upfName") String upfName,
                      @Param("expectedUserCount") Integer expectedUserCount,
                      @Param("requestedOpenDate") String requestedOpenDate,
                      @Param("remark") String remark);
}
