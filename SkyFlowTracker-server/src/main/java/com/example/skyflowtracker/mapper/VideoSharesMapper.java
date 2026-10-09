package com.example.skyflowtracker.mapper;

import com.example.skyflowtracker.pojo.VideoShares;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface VideoSharesMapper {

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("INSERT INTO video_shares (share_code, task_id, user_id, expire_time, view_count, create_time) " +
            "VALUES (#{shareCode}, #{taskId}, #{userId}, #{expireTime}, #{viewCount}, #{createTime})")
    int insertShare(VideoShares share);

    @Select("SELECT * FROM video_shares WHERE share_code = #{shareCode}")
    VideoShares selectByShareCode(String shareCode);

    @Update("UPDATE video_shares SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(Long id);

    @Delete("DELETE FROM video_shares WHERE id = #{id} AND user_id = #{userId}")
    int deleteShareByIdAndUserId(@Param("id") Long id, @Param("userId") String userId);

    @Select("SELECT share_code FROM video_shares WHERE id = #{id}")
    String selectShareCodeById(Long id);

    List<Map<String, Object>> selectShareListByUserId(@Param("userId") String userId, @Param("offset") int offset, @Param("pageSize") int pageSize);

    int countSharesByUserId(@Param("userId") String userId);

    List<Map<String, Object>> selectAllShareList(@Param("offset") int offset, @Param("pageSize") int pageSize);

    int countAllShares();

    @Delete("DELETE FROM video_shares WHERE id = #{id}")
    int deleteShareById(Long id);
}
