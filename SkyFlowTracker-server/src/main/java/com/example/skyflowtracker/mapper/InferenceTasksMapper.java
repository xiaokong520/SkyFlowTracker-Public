package com.example.skyflowtracker.mapper;

import com.example.skyflowtracker.pojo.InferenceTasks;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface InferenceTasksMapper {

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("insert into inference_tasks (flight_id, user_id, model_name, status, start_time, create_time, modification_time) " +
            "values (#{flightId}, #{userId}, #{modelName}, #{status}, #{startTime}, #{createTime}, #{modificationTime})")
    int insertTask(InferenceTasks task);

    @Select("select * from inference_tasks where id = #{id}")
    InferenceTasks selectTasksById(Long id);

    int updateTask(InferenceTasks task);

    List<Map<String, Object>> selectTasksList(String userId,Long flightId,int offset,int pageSize);

    int countTasks(String userId, Long flightId);

    int deleteTasks(List<Long> ids);
}
