package com.pm.authservice.controller;

import com.pm.authservice.dto.LoginRequestDto;
import com.pm.authservice.dto.LoginResponseDto;
import com.pm.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
public class AuthController {

    @Autowired
    private  AuthService authService;


    @Operation(summary = "Generate token on user login")
    @PostMapping("/login")

    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto loginRequestDto) {
        Optional<String> tokenOptional = authService.authenticate(loginRequestDto);
        if (tokenOptional.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);

        }

        String token = tokenOptional.get();
        return new ResponseEntity<>(new LoginResponseDto(token), HttpStatus.OK);
    }

}
