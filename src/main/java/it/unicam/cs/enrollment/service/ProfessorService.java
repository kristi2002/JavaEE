package it.unicam.cs.enrollment.service;

import it.unicam.cs.enrollment.common.logging.Loggable;
import it.unicam.cs.enrollment.domain.model.Professor;
import it.unicam.cs.enrollment.exception.ResourceNotFoundException;
import it.unicam.cs.enrollment.repository.ProfessorRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Read-only access to teaching staff.
 *
 * <p>Kept deliberately thin. A service that does nothing but forward to a
 * repository is sometimes criticised as a pointless layer, and the criticism has
 * merit - but the consistency is worth more than the saved indirection: every
 * REST resource talks to a service, always, so nobody has to remember which
 * endpoints are allowed to reach into the repository directly. It is also the
 * place the first real rule will land when one appears.
 */
@Loggable
@ApplicationScoped
public class ProfessorService {

    private ProfessorRepository professorRepository;

    protected ProfessorService() {
        // required by CDI
    }

    @Inject
    public ProfessorService(ProfessorRepository professorRepository) {
        this.professorRepository = professorRepository;
    }

    @Transactional
    public List<Professor> findAll() {
        return professorRepository.findAllOrdered();
    }

    @Transactional
    public Professor findById(Long id) {
        return professorRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Professor", id));
    }
}
