package it.unicam.cs.enrollment.service;

import it.unicam.cs.enrollment.domain.model.StudentStatus;
import it.unicam.cs.enrollment.repository.EnrollmentRepository;
import it.unicam.cs.enrollment.repository.StudentRepository;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Scheduled housekeeping, using the EJB TIMER SERVICE.
 *
 * <h2>Why this is an EJB and not a CDI bean</h2>
 * Almost everything else in this application is a plain CDI bean, because CDI
 * has absorbed most of what EJB used to be needed for. Scheduling is one of the
 * things it has NOT absorbed: {@code @Schedule} is an EJB feature, so a
 * scheduled component is still an {@code @Singleton} session bean.
 *
 * <p>{@code jakarta.ejb.Singleton} - note the package. It is NOT
 * {@code jakarta.inject.Singleton}, and the two behave differently. The EJB one
 * gives you container-managed concurrency and transactions; the CDI one is just
 * a scope. Mixing them up produces genuinely confusing bugs.
 *
 * <h2>{@code @ConcurrencyManagement(CONTAINER)}</h2>
 * The default for an EJB singleton, and it means the container serialises access
 * with a write lock: only one thread is inside this bean at a time. That is
 * exactly what you want for a job that must not overlap with itself.
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
public class EnrollmentMaintenanceJob {

    private static final Logger LOG = LoggerFactory.getLogger(EnrollmentMaintenanceJob.class);

    @Inject
    private EnrollmentRepository enrollmentRepository;

    @Inject
    private StudentRepository studentRepository;

    @Inject
    private Clock clock;

    /**
     * Nightly sweep: withdraws enrollments left ACTIVE from previous academic
     * years.
     *
     * <h3>Reading a {@code @Schedule} expression</h3>
     * It is cron, with named attributes. Unspecified time fields default to
     * {@code 0}, and unspecified date fields default to {@code *}, so the
     * declaration below means "03:00 every day".
     * <pre>
     *   second="0" minute="0" hour="3"   -&gt; 03:00:00
     *   dayOfMonth="*" month="*" year="*" dayOfWeek="*"
     * </pre>
     * Useful variants: {@code minute="*&#47;15"} (every quarter hour),
     * {@code dayOfWeek="Mon-Fri"}, {@code hour="9-17"}.
     *
     * <h3>{@code persistent = false} - read this before shipping anything</h3>
     * A PERSISTENT timer (the default) is stored in the server's timer database
     * and survives restarts. That sounds desirable until you run more than one
     * instance: every node reads the same timer store and you get duplicate
     * executions, or fight over a database lock. A NON-PERSISTENT timer lives in
     * memory, belongs to its node, and is recreated on startup.
     *
     * <p>In a clustered deployment neither is sufficient on its own - you need a
     * distributed lock or a dedicated scheduler (Quartz clustered, Kubernetes
     * CronJob). Knowing that this is a genuinely hard problem, rather than
     * assuming {@code @Schedule} handles it, is the takeaway.
     *
     * <h3>{@code REQUIRES_NEW}</h3>
     * A timer callback has no caller and therefore no inbound transaction, so
     * this is really just documentation of intent - but stating it makes the
     * boundary obvious to the next reader.
     */
    @Schedule(hour = "3", minute = "0", second = "0", persistent = false,
            info = "Nightly stale-enrollment sweep")
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void closeStaleEnrollments() {
        int currentAcademicYear = clock.instant().atZone(ZoneOffset.UTC).getYear();

        LOG.info("Starting stale-enrollment sweep for academic years before {}", currentAcademicYear);

        int affected = enrollmentRepository.closeStaleEnrollments(currentAcademicYear);

        // Logging the count matters. A job that silently does nothing looks
        // exactly like a job that is broken; a job that reports "0 rows" tells
        // you it ran and there was nothing to do.
        LOG.info("Stale-enrollment sweep finished: {} enrollment(s) withdrawn", affected);
    }

    /**
     * A lightweight heartbeat that also emits basic metrics.
     *
     * <p>Runs every five minutes so that you can actually SEE the timer service
     * working while the container is up - watch for it in
     * {@code docker compose logs -f wildfly}.
     *
     * <p>In a production system these numbers would go to a metrics backend
     * (MicroProfile Metrics, Micrometer, Prometheus) rather than to the log, so
     * they could be graphed and alerted on. Logging them is the zero-dependency
     * version of the same idea.
     */
    @Schedule(minute = "*/5", hour = "*", persistent = false,
            info = "Enrollment statistics heartbeat")
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void reportStatistics() {
        long active = studentRepository.countByStatus(StudentStatus.ACTIVE);
        long suspended = studentRepository.countByStatus(StudentStatus.SUSPENDED);

        LOG.info("[METRICS] students.active={} students.suspended={}", active, suspended);
    }
}
