package edplatform.edplat.entities.courses;

import edplatform.edplat.controllers.controllerUtils.AuthenticationUpdater;
import edplatform.edplat.entities.assignment.Assignment;
import edplatform.edplat.entities.authority.Authority;
import edplatform.edplat.entities.courses.enrollment.CourseEnrollment;
import edplatform.edplat.entities.courses.enrollment.EnrollmentRequestCreationStrategy;
import edplatform.edplat.entities.courses.enrollment.EnrollmentRequestCreationStrategyFactory;
import edplatform.edplat.exceptions.EnrollRequestAlreadySentException;
import edplatform.edplat.repositories.AssignmentRepository;
import edplatform.edplat.entities.authority.AuthorityService;
import edplatform.edplat.entities.users.User;
import edplatform.edplat.repositories.CourseEnrollRequestRepository;
import edplatform.edplat.repositories.CourseRepository;
import edplatform.edplat.repositories.UserRepository;
import edplatform.edplat.security.AuthorityStringBuilder;
import edplatform.edplat.security.SecurityAuthorizationChecker;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CourseServiceImpl implements CourseService {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityStringBuilder authorityStringBuilder;

    @Autowired
    private AuthorityService authorityService;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private CourseEnrollRequestRepository courseEnrollRequestRepository;

    @Autowired
    private SecurityAuthorizationChecker securityAuthorizationChecker;

    @Autowired
    private EnrollmentRequestCreationStrategyFactory enrollmentRequestCreationStrategyFactory;

    @Override
    public Optional<Course> findById(Long id) {
        return courseRepository.findById(id);
    }

    @Override
    public Optional<Course> findByIdWithAssignments(Long id) {
        return courseRepository.findByIdWithAssignments(id);
    }

    @Override
    public Optional<Course> findByCourseName(String courseName) {
        return courseRepository.findByCourseName(courseName);
    }

    @Override
    public Page<Course> findAll(Pageable pageable) {
        return courseRepository.findAll(pageable);
    }

    @Override
    public void save(Course course) {
        log.info("Saving course with name {} at timestamp {}",
                course.getCourseName(), course.getCreatedAt());

        courseRepository.save(course);
    }

    @Override
    @Transactional
    public void createCourse(Long userId, Course course) {
        User user;
        try {
            user = userRepository.findByIdWithCourses(userId).orElseThrow();
        } catch (NoSuchElementException e) {
            log.error("Can't create course with nonexistent user with id {}", userId);
            return;
        }

        createCourse(user, course);
    }

    @Override
    @Transactional
    public void createCourse(User user, Course course) {
        log.info("Creating course with name {} for user with email {}", course.getCourseName(), user.getEmail());

        // add course to user:
        if (course.getUsers() == null) {
            course.setUsers(new ArrayList<>());
        }
        course.getUsers().add(user);
        if (user.getCourses() == null) {
            user.setCourses(new ArrayList<>());
        }
        user.getCourses().add(course);

        // add the time it was added to the course:
        updateCourseTimestamp(course);
        this.save(course);

        // retrieve from DB to get id for authority creation:
        course = courseRepository.findByCourseName(course.getCourseName()).get();
        // add the course owner authority to the user that created the course:
        String authorityName = authorityStringBuilder.getCourseOwnerAuthority(course.getId().toString());
        authorityService.giveAuthorityToUser(user, authorityName);
        // ensure that the enrolled authority for the course is created:
        authorityService.createEnrolledAuthority(course.getId());
    }

    @Override
    public void addAssignmentToCourse(Assignment assignment, Long courseId) throws Exception {
        Optional<Course> optionalCourse = courseRepository.findByIdWithAssignments(courseId);
        Course course;
        if (optionalCourse.isPresent()) {
            course = optionalCourse.get();
        } else {
            throw new Exception("Error retrieving course from database");
        }

        assignment.setCourse(course);
        course.getAssignments().add(assignment);
        // update timestamp(since course was modified):
        updateCourseTimestamp(course);
        assignmentRepository.save(assignment);
    }

    @Override
    @Transactional
    public void enrollUserToCourse(Long courseId, Long userId, Authentication authentication) throws EnrollRequestAlreadySentException {
        Course course = courseRepository.findById(courseId).get();
        User user = userRepository.findByIdWithCourses(userId).get();

        // check if user already has course:
        if (user.getCourses().contains(course)) {
            log.error("User {} already has course {}", user.getEmail(), course.getCourseName());
            return;
        }

        if (!Hibernate.isInitialized(course.getUsers())) {
            course.setUsers(userRepository.findAllByCourse(course));
        }

        // check if request was already sent:
        if (courseEnrollRequestRepository.findByCourseAndUser(course, user).isPresent()) {
            throw new EnrollRequestAlreadySentException("User already requested enrollment");
        }

        EnrollmentRequestCreationStrategy strategy = enrollmentRequestCreationStrategyFactory.creationStrategy(course.getEnrollmentType());
        strategy.enroll(user, course, authentication);
    }

    @Override
    @Transactional
    public List<User> getOwnerUsers(Course course) {
        course.setUsers(userRepository.findAllWithAuthorities(course.getCourseName()));
        return course.getUsers().stream()
                .filter(user -> securityAuthorizationChecker.checkCourseOwner(user, course.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteCourse(Course course) {
        // delete all authorities regarding course:
        // for owners:
        String ownerAuthority = authorityStringBuilder.getCourseOwnerAuthority(course.getId().toString());
        authorityService.deleteAuthority(ownerAuthority);
        // for enrolled:
        String enrolledAuthority = authorityStringBuilder.getCourseEnrolledAuthority(course.getId().toString());
        authorityService.deleteAuthority(enrolledAuthority);

        // delete users to synchronize this part of the relationship
        // (otherwise deletion is not visible in transaction):
        List<User> users = userRepository.findAllWithCourses(course);
        for (User user :
                users) {
            user.getCourses().remove(course);
        }
        // delete all assignments and the course itself (delete is cascading):
        course.setUsers(new ArrayList<>());
        courseRepository.delete(course);
    }

    private void updateCourseTimestamp(Course course) {
        Timestamp courseCreatedAt = new Timestamp(System.currentTimeMillis());
        course.setCreatedAt(courseCreatedAt);
    }

    @Override
    public Page<Course> findAllByCourseNameContains(String contains, Pageable pageable) {
        return courseRepository.findAllByCourseNameContains(contains, pageable);
    }

    @Override
    public Page<Course> findAllByDescriptionContains(String containsDescription, Pageable pageable) {
        return courseRepository.findAllByDescriptionContains(containsDescription, pageable);
    }
}
