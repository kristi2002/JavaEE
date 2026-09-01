package it.unicam.cs.enrollment.repository;

import it.unicam.cs.enrollment.domain.model.Professor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link Professor}.
 *
 * <p>Deliberately minimal. Not every repository needs to be elaborate - most
 * production repositories look like this one, and only a couple carry the
 * complexity you see in {@link StudentRepository} and {@link CourseRepository}.
 * Adding methods "because we might need them" is how a codebase accumulates
 * dead code; add them when a use case asks.
 */
@ApplicationScoped
public class ProfessorRepository extends AbstractJpaRepository<Professor> {

    public ProfessorRepository() {
        super(Professor.class);
    }

    public Optional<Professor> findByStaffNumber(String staffNumber) {
        TypedQuery<Professor> query = em()
                .createNamedQuery(Professor.FIND_BY_STAFF_NUMBER, Professor.class)
                .setParameter("staffNumber", staffNumber);
        return singleResult(query);
    }

    public List<Professor> findAllOrdered() {
        return em().createNamedQuery(Professor.FIND_ALL_ORDERED, Professor.class)
                .getResultList();
    }

    public boolean existsByStaffNumber(String staffNumber) {
        Long count = em().createQuery(
                        "SELECT COUNT(p) FROM Professor p WHERE p.staffNumber = :staffNumber",
                        Long.class)
                .setParameter("staffNumber", staffNumber)
                .getSingleResult();
        return count > 0;
    }
}
