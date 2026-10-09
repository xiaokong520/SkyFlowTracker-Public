package com.example.skyflowtracker.mapper;

import com.example.skyflowtracker.dto.LoginDto;
import com.example.skyflowtracker.dto.RegisterDto;
import com.example.skyflowtracker.dto.UpdateUserInfoDto;
import com.example.skyflowtracker.pojo.Users;
import org.apache.catalina.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UsersMapper {

    @Select("select * from users where user_name = #{userName}")
    Users selectUserByUserName(String userName);
    @Select("select * from users where user_id = #{userId}")
    Users selectUserByUserId(String userId);
    @Select("select * from users where email = #{email}")
    Users selectUserByEmail(String email);
    @Select("select * from users order by create_time desc limit #{pageSize} offset #{offset};")
    List<Users> selectUsersList(int offset, Integer pageSize);
    @Select("select count(*) from users;")
    int countUsers();

    @Insert("insert into users(user_id,user_name,password,email,nick_name,create_time,modification_time,status,role)" +
            " values (#{userId},#{userName},#{password},#{email},#{nickName},#{createTime},#{modificationTime},#{status},#{role});")
    int addUser(RegisterDto registerDto);

    Users login(LoginDto loginDto);

    @Update("update users set ip = #{ip},modification_time = #{modificationTime},last_login_time = #{lastLoginTime} where user_id = #{userId}")
    int updateIp(Users users);
    @Update("update users set password=#{password},modification_time=#{modificationTime} where user_id=#{userId}")
    int updatePassword(Users user);
    @Update("update users set avatar=#{avatar},modification_time = #{modificationTime} where user_id=#{userId}")
    int updateAvatar(Users users);
    int updateUserInfo(UpdateUserInfoDto updateUserInfoDto);

    int deleteUsers(List<String> userIds);
}
