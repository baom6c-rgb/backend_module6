package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.service.AdminService;
import lombok.RequiredArgsConstructor; // Sử dụng Lombok
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<List<User>> getAll() {
        return ResponseEntity.ok(adminService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getById(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getById(id));
    }

    @GetMapping("/students")
    public ResponseEntity<List<User>> getActiveStudents() {
        List<User> students = adminService.findByStatus(UserStatus.ACTIVE);
        return ResponseEntity.ok(students);
    }

    @PostMapping
    public ResponseEntity<User> add(@RequestBody User user) {
        User createdUser = adminService.add(user);
        return new ResponseEntity<>(createdUser, HttpStatus.CREATED); // Trả về 201
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> update(@PathVariable Long id, @RequestBody User user) {
        return ResponseEntity.ok(adminService.update(id, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminService.delete(id);
        return ResponseEntity.noContent().build(); // Trả về 204
    }
}