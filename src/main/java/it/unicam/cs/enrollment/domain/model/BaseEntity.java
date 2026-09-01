package it.unicam.cs.enrollment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Version;

import java.io.Serializable;
import java.time.Instant;

/**
 * Common state and behaviour shared by every entity in the system.
 *
 * <h2>Why {@code @MappedSuperclass} and not {@code @Entity}?</h2>
 * {@code @MappedSuperclass} is pure code reuse: its fields are copied into each
 * subclass's own table, and it has no table of its own. You cannot query it and
 * you cannot have a relationship pointing at it. That is exactly what we want
 * here - {@code Student} and {@code Course} share an {@code id} column but are
 * in no way "the same kind of thing".
 *
 * <p>Contrast with the three real inheritance strategies, which you should be
 * able to name in an interview:
 * <ul>
 *   <li>{@code SINGLE_TABLE} - all subclasses in one wide table plus a
 *       discriminator column. Fast, but forces nullable columns.</li>
 *   <li>{@code JOINED} - one table per class, joined by primary key. Properly
 *       normalised, costs a JOIN per query.</li>
 *   <li>{@code TABLE_PER_CLASS} - a full table per concrete class. Polymorphic
 *       queries become UNIONs; usually avoided.</li>
 * </ul>
 *
 * <h2>Fields that appear in almost every production entity</h2>
 * The {@code id / version / createdAt / updatedAt} quartet below is close to
 * universal in enterprise codebases. Putting it in a base class means you write
 * it once instead of forgetting it somewhere.
 */
@MappedSuperclass
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * SURROGATE KEY - a meaningless number owned by the database, as opposed to
     * a NATURAL KEY like a student number that carries business meaning.
     *
     * <p>Surrogate keys are the default choice in industry because business
     * meaning changes: a university might renumber its students, but a foreign
     * key pointing at row 42 never needs to move.
     *
     * <h3>Why SEQUENCE and not IDENTITY?</h3>
     * With {@code IDENTITY} the database assigns the id during the INSERT, so
     * Hibernate must execute the INSERT immediately on {@code persist()} and
     * can never batch writes. With {@code SEQUENCE} Hibernate asks for ids up
     * front and can group many INSERTs into one JDBC batch.
     *
     * <p>{@code allocationSize = 50} is the "pooled optimizer": one round trip
     * to the sequence buys 50 ids held in memory. Fewer round trips, at the
     * cost of gaps in the id numbering after a restart. Gaps are harmless -
     * ids are identifiers, not counters.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "app_id_generator")
    @SequenceGenerator(name = "app_id_generator", sequenceName = "app_id_seq", allocationSize = 50)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * OPTIMISTIC LOCKING. Hibernate adds {@code AND version = ?} to every
     * UPDATE and increments the column. If two transactions read the same row
     * and both try to write, the second one updates zero rows and the provider
     * throws {@link jakarta.persistence.OptimisticLockException}.
     *
     * <p>This is how you prevent the LOST UPDATE problem without holding a
     * database lock for the whole conversation. Compare:
     * <ul>
     *   <li><b>Optimistic</b> - assume conflicts are rare, detect them at
     *       commit. Scales well. The default choice.</li>
     *   <li><b>Pessimistic</b> - {@code SELECT ... FOR UPDATE}, blocking other
     *       writers. Correct but serialises access; use only for genuinely
     *       contended rows (we use it for course capacity).</li>
     * </ul>
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * AUDIT COLUMNS. Almost every real table has them; when something looks
     * wrong in production, "when was this row created or last touched?" is the
     * first question asked.
     *
     * <p>{@code Instant} is a point on the UTC timeline. Store timestamps as
     * {@code Instant} (or {@code OffsetDateTime}), never as {@code LocalDateTime},
     * which has no timezone and silently means different moments on different
     * servers. Convert to local time only when rendering for a human.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * JPA ENTITY LIFECYCLE CALLBACKS. The provider invokes these around the
     * persistence operations, so the timestamps are maintained by the model
     * itself and no service can forget to set them.
     *
     * <p>The full set is {@code @PrePersist @PostPersist @PreUpdate @PostUpdate
     * @PreRemove @PostRemove @PostLoad}. Keep them trivial: they run inside the
     * flush, where you must not touch the EntityManager or call other services.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Convenience check used by the generic repository to decide between
     * {@code persist} (new row) and {@code merge} (existing row).
     */
    public boolean isNew() {
        return id == null;
    }

    /**
     * EQUALS/HASHCODE FOR JPA ENTITIES - a classic source of subtle bugs.
     *
     * <p>Three constraints fight each other:
     * <ol>
     *   <li>A new entity has {@code id == null}. If you put it in a
     *       {@code HashSet} and then persist it, an id-based {@code hashCode}
     *       would change and the entity would be lost inside the set.</li>
     *   <li>Hibernate hands you PROXIES for lazy associations. A proxy is a
     *       generated subclass, so {@code getClass()} is not the entity class
     *       and {@code this.getClass() == other.getClass()} fails.</li>
     *   <li>Field access on a proxy returns null - you must go through the
     *       getter so the proxy initialises itself.</li>
     * </ol>
     *
     * <p>The pattern below is the widely accepted answer: {@code instanceof}
     * (proxy-friendly), comparison through the getter, a null id never equal to
     * anything, and a {@code hashCode} that is constant for the lifetime of the
     * object. Entities of the same type share a hash bucket, which is fine -
     * correctness beats a marginally better hash distribution.
     *
     * <p>Where a real business key exists (a student number, a course code) the
     * subclass overrides these with that key. That is the more precise answer
     * and what Domain-Driven Design recommends.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity)) {
            return false;
        }
        BaseEntity that = (BaseEntity) other;
        return this.getId() != null && this.getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", version=" + version + "}";
    }
}
