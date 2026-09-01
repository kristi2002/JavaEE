package it.unicam.cs.enrollment.api.mapper;

import it.unicam.cs.enrollment.api.dto.response.ProfessorResponse;
import it.unicam.cs.enrollment.domain.model.Professor;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts {@link Professor} entities to their API representation.
 */
@ApplicationScoped
public class ProfessorMapper {

    public ProfessorResponse toResponse(Professor professor) {
        ProfessorResponse response = new ProfessorResponse();

        response.setId(professor.getId());
        response.setStaffNumber(professor.getStaffNumber());
        response.setFullName(professor.fullName());
        response.setEmail(professor.getEmail() != null ? professor.getEmail().getValue() : null);
        response.setTitle(professor.getTitle().name());
        // Both the enum name and its Italian label: the first is for code that
        // switches on it, the second for a UI that displays it. Sending both is
        // cheaper than making every client hard-code the translation.
        response.setItalianTitle(professor.getTitle().getItalianTitle());
        response.setDepartment(professor.getDepartment());

        return response;
    }

    public List<ProfessorResponse> toResponseList(List<Professor> professors) {
        return professors.stream().map(this::toResponse).collect(Collectors.toList());
    }
}
