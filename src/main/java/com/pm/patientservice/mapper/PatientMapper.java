package com.pm.patientservice.mapper;

import com.pm.patientservice.dto.PatientRequestDTO;
import com.pm.patientservice.dto.PatientResponseDTO;
import com.pm.patientservice.model.Patient;

import java.time.LocalDate;

public class PatientMapper {
    public static PatientResponseDTO toDTO(Patient patient){
        PatientResponseDTO responseDTO = new PatientResponseDTO();
        responseDTO.setPatientId(patient.getId().toString());
        responseDTO.setName(patient.getName());
        responseDTO.setAddress(patient.getAddress());
        responseDTO.setEmail(patient.getEmail());
        responseDTO.setDateOfBirth(patient.getDateOfBirth().toString());
        return responseDTO;
    }

    public static Patient toModle(PatientRequestDTO patientRequestDTO){
        Patient patient = new Patient();
        patient.setName(patientRequestDTO.getName());
        patient.setAddress(patientRequestDTO.getAddress());
        patient.setEmail(patientRequestDTO.getEmail());
        patient.setDateOfBirth(LocalDate.parse(patientRequestDTO.getDateOfBirth()));
        patient.setRegisteredDate(LocalDate.parse(patientRequestDTO.getRegisteredDate()));

        return patient;
    }
}
