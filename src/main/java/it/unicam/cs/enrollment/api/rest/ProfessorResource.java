package it.unicam.cs.enrollment.api.rest;

import it.unicam.cs.enrollment.api.dto.response.ProfessorResponse;
import it.unicam.cs.enrollment.api.mapper.ProfessorMapper;
import it.unicam.cs.enrollment.service.ProfessorService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Read-only endpoints for teaching staff.
 *
 * <p>Exists chiefly so that a client creating a course can discover valid
 * {@code professorId} values. An API that requires an id but offers no way to
 * find one is a surprisingly common oversight - always ask "how does the caller
 * obtain every value I demand?".
 */
@Path("/professors")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class ProfessorResource {

    @Inject
    private ProfessorService professorService;

    @Inject
    private ProfessorMapper professorMapper;

    @GET
    public List<ProfessorResponse> findAll() {
        return professorMapper.toResponseList(professorService.findAll());
    }

    @GET
    @Path("/{id}")
    public ProfessorResponse findById(@PathParam("id") Long id) {
        return professorMapper.toResponse(professorService.findById(id));
    }
}
