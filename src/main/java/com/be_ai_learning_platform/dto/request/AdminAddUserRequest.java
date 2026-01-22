package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminAddUserRequest {
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;

    @NotBlank(message = "Mật khẩu tạm thời không được để trống")
    private String password;

    @NotBlank(message = "Vui lòng chọn quyền (Role)")
    private String roleName;

    @NotNull(message = "ID lớp không được trống")
    private Long classId;

    @NotNull(message = "ID học phần không được trống")
    private Long moduleId;
}