package edplatform.edplat.repositories;

import edplatform.edplat.entities.authority.Authority;
import edplatform.edplat.entities.courses.Course;
import edplatform.edplat.entities.users.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import javax.persistence.QueryHint;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * Loads a user by email and all its courses
     * @param email email of the user to be retrieved
     * @return user object with all its courses
     */
    @QueryHints(value = { @QueryHint(name = "QueryHints.PASS_DISTINCT_THROUGH", value = "false")})
    @Query("select distinct u from User u left join fetch u.courses where u.email = :email")
    Optional<User> findByEmailWithCourses(String email);

    /**
     * Loads a user by id and all its courses
     * @param id id of the user to be retrieved
     * @return user object with all its courses
     */
    @QueryHints(value = { @QueryHint(name = "QueryHints.PASS_DISTINCT_THROUGH", value = "false")})
    @Query("select distinct u from User u left join fetch u.courses where u.id = :id")
    Optional<User> findByIdWithCourses(Long id);

    /**
     * Loads all users by course, together with their courses
     * @param course course of the users to be retrieved
     * @return users list
     */
    @QueryHints(value = { @QueryHint(name = "QueryHints.PASS_DISTINCT_THROUGH", value = "false")})
    @Query("select distinct u from User u join fetch u.courses c where c = :course")
    List<User> findAllWithCourses(Course course);

    /**
     * Loads all users by course
     * @param course course of the users to be retrieved
     * @return users list
     */
    @QueryHints(value = { @QueryHint(name = "QueryHints.PASS_DISTINCT_THROUGH", value = "false")})
    @Query("select distinct u from User u join u.courses c where c = :course")
    List<User> findAllByCourse(Course course);

    /**
     * Loads all users that own/are enrolled in the course, together with their authorities
     * @param courseName name of the course of the users to be retrieved
     * @return users list
     */
    @QueryHints(value = { @QueryHint(name = "QueryHints.PASS_DISTINCT_THROUGH", value = "false")})
    @Query("select distinct u from User u left join fetch u.authorities join u.courses c where c.courseName = :courseName")
    List<User> findAllWithAuthorities(String courseName);
}
