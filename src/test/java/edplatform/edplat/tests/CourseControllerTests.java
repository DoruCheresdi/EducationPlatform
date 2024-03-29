package edplatform.edplat.tests;

import edplatform.edplat.entities.assignment.Assignment;
import edplatform.edplat.entities.courses.enrollment.CourseEnrollment;
import edplatform.edplat.repositories.AssignmentRepository;
import edplatform.edplat.entities.authority.Authority;
import edplatform.edplat.entities.courses.Course;
import edplatform.edplat.repositories.CourseRepository;
import edplatform.edplat.entities.courses.CourseService;
import edplatform.edplat.entities.submission.Submission;
import edplatform.edplat.repositories.SubmissionRepository;
import edplatform.edplat.entities.users.CustomUserDetails;
import edplatform.edplat.entities.users.User;
import edplatform.edplat.repositories.UserRepository;
import edplatform.edplat.entities.users.UserService;
import edplatform.edplat.security.AuthorityStringBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc()
public class CourseControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseService courseService;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private AuthorityStringBuilder authorityStringBuilder;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Test
    void shouldGetListContainingAllCourses() throws Exception {
        UserDetails userDetails = getSimpleUserDetails();

        mvc.perform(get("/course/courses").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("courses_paged"));
    }

    @Test
    @Transactional
    void shouldCreateAndPersistCourse() throws Exception {
        Course course = getSimpleCourse();

        CustomUserDetails userDetails = (CustomUserDetails) getSimpleUserDetails();
        userRepository.save(userDetails.getUser());

        mvc.perform(post("/course/process_course")
                        .param("courseName", course.getCourseName())
                        .param("description",  course.getDescription())
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().isOk());

        Optional<Course> retrievedCourseOptional = courseRepository.findByCourseName(course.getCourseName());

        assertThat(retrievedCourseOptional.isPresent()).isEqualTo(true);

        Course retrievedCourse = retrievedCourseOptional.get();

        // check if it is the same course:
        assertThat(retrievedCourse.getDescription()).isEqualTo(course.getDescription());

        // check that the user is given the course:
        User retrievedUser = userRepository.findByEmailWithCourses(userDetails.getUser().getEmail()).get();
        assertThat(retrievedUser.getCourses().get(0)).isEqualTo(retrievedCourse);

        // test that the authority given is right:
        assertThat(retrievedUser.getAuthorities().size()).isEqualTo(1);

        Long id = retrievedCourse.getId();
        String authority = authorityStringBuilder.getCourseOwnerAuthority(id.toString());
        assertThat(retrievedUser.getAuthorities().iterator().next().getAuthority()).isEqualTo(authority);

        // cleanup:
        courseService.deleteCourse(retrievedCourse);
    }

    @Test
    void shouldDeleteSimpleCourse() throws Exception {
        Course course = getSimpleCourse();

        courseRepository.save(course);

        course = courseRepository.findByCourseName(course.getCourseName()).get();

        CustomUserDetails userDetails = (CustomUserDetails) getSimpleUserDetails();
        giveCourseOwnerAuthorityToUserDetails(userDetails, course);

        mvc.perform(post("/course/delete")
                        .param("courseId", course.getId().toString())
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("course_deletion_success"));

        // course must be deleted, therefore it must not be found in the query:
        assertThat(courseRepository.findByCourseName(course.getCourseName()).isPresent()).isEqualTo(false);
    }

    @Test
    void shouldFailDeletingSimpleNonexistentCourse() throws Exception {
        Course course = getSimpleCourse();

        courseRepository.save(course);

        course = courseRepository.findByCourseName(course.getCourseName()).get();

        CustomUserDetails userDetails = (CustomUserDetails) getSimpleUserDetails();

        giveCourseOwnerAuthorityToUserDetails(userDetails, course);

        courseRepository.delete(course);

        assertThat(courseRepository.findByCourseName("TestControllerCourse").isPresent())
                .isEqualTo(false);

        // now trying to delete nonexisting course, should give error page as a response:
        mvc.perform(post("/course/delete")
                        .param("courseId", course.getId().toString())
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("error"));
    }

    @Test
    void shouldDeleteCourseWithAssignmentsSubmissionsAndAuthorities() throws Exception {
        // create entities:
        CustomUserDetails userDetails = (CustomUserDetails) getSimpleUserDetails();

        Course course = getSimpleCourse();
        course.setUsers(Arrays.asList(userDetails.getUser()));
        userDetails.getUser().setCourses(new ArrayList<>());
        userDetails.getUser().getCourses().add(course);

        Assignment assignment = new Assignment();
        assignment.setAssignmentName("TestAssignment");
        assignment.setCourse(course);
        assignment.setGradeWeight(2);
        course.setAssignments(Arrays.asList(assignment));

        Submission submission = new Submission();
        submission.setUser(userDetails.getUser());
        submission.setSubmissionResource("submissionResource");
        submission.setAssignment(assignment);
        assignment.setSubmissions(Arrays.asList(submission));

        courseRepository.save(course);

        // retrieve entities to check them later:
        assignment = assignmentRepository.findWithSubmissionsByAssignmentName("TestAssignment").get();
        submission = assignment.getSubmissions().get(0);
        Long submissionId = submission.getId();
        assertThat(submissionRepository.findById(submissionId).isPresent()).isEqualTo(true);

        course = courseRepository.findByCourseName(course.getCourseName()).get();
        giveCourseOwnerAuthorityToUserDetails(userDetails, course);

        mvc.perform(post("/course/delete")
                        .param("courseId", course.getId().toString())
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("course_deletion_success"));

        // course must be deleted, therefore it must not be found in the query:
        assertThat(courseRepository.findByCourseName(course.getCourseName()).isPresent()).isEqualTo(false);
        assertThat(assignmentRepository.findByAssignmentName("TestAssignment").isPresent()).isEqualTo(false);
        assertThat(submissionRepository.findById(submissionId).isPresent()).isEqualTo(false);

        // cleanup:
        userService.deleteUser(userDetails.getUser());
    }

    @Test
    public void shouldChangeDescription() throws Exception {
        // create entities:
        CustomUserDetails userDetails = (CustomUserDetails) getSimpleUserDetails();

        Course course = getSimpleCourse();
        userDetails.getUser().setCourses(new ArrayList<>(List.of(course)));

        courseService.createCourse(userDetails.getUser(), course);

        String newDescription = "newDescription";
        mvc.perform(post("/course/change_description")
                        .param("courseId", course.getId().toString())
                        .param("newDescription", newDescription)
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().is3xxRedirection());

        Course retrievedCourse = courseRepository.findByCourseName("TestControllerCourse").get();
        assertThat(retrievedCourse.getDescription()).isEqualTo(newDescription);

        // cleanup:
        userService.deleteUser(userDetails.getUser());
    }

    @Test
    public void shouldEnrollUserInCourseWithFreeEnrollment() throws Exception {
        // create entities:
        CustomUserDetails userDetails = (CustomUserDetails) getSimpleUserDetails();

        // student to enroll:
        CustomUserDetails userDetailsStudent = (CustomUserDetails) getSimpleUserDetails();
        userDetailsStudent.getUser().setEmail("testStudent@test.com");
        userDetailsStudent.getUser().setLastName("testStudent");
        userDetailsStudent.getUser().setFirstName("testStudent");

        Course course = getSimpleCourse();
        userDetails.getUser().setCourses(new ArrayList<>(List.of(course)));

        courseService.createCourse(userDetails.getUser(), course);
        userService.save(userDetailsStudent.getUser());

        mvc.perform(post("/course/enroll")
                        .param("courseId", course.getId().toString())
                        .with(csrf())
                        .with(user(userDetailsStudent)))
                .andExpect(status().is3xxRedirection());

        Course retrievedCourse = courseRepository.findByCourseName(course.getCourseName()).get();
        retrievedCourse.setUsers(userRepository.findAllByCourse(retrievedCourse));
        assertThat(retrievedCourse.getUsers().contains(userDetailsStudent.getUser()));
        assertThat(userDetailsStudent.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals(authorityStringBuilder.getCourseEnrolledAuthority(course.getId().toString()))));

        // cleanup:
        userService.deleteUser(userDetails.getUser());
        userService.deleteUser(userDetailsStudent.getUser());
    }

    private void giveCourseOwnerAuthorityToUserDetails(CustomUserDetails userDetails, Course course) {
        userDetails.getUser().getAuthorities().add(
                new Authority(
                        authorityStringBuilder.getCourseOwnerAuthority(
                                course.getId().toString())));
    }

    private UserDetails getSimpleUserDetails() {
        User user = new User();
        user.setEmail("test@springTest.com");
        user.setFirstName("testFirstName");
        user.setLastName("testLastName");

        String encodedPassword = passwordEncoder.encode("test");
        user.setPassword(encodedPassword);

        user.setCourses(new ArrayList<>());
        user.setSubmissions(new ArrayList<>());

        return new CustomUserDetails(user);
    }

    private Course getSimpleCourse() {
        Course course = new Course();
        course.setCourseName("TestControllerCourse");
        course.setDescription("TestCourseControllerDescription");
        course.setUsers(new ArrayList<>());
        course.setEnrollmentType(CourseEnrollment.EnrollmentType.FREE);
        return course;
    }

    private Course getCourse(String courseName, String courseDescription) {
        Course course = new Course();
        course.setCourseName(courseName);
        course.setDescription(courseDescription);
        course.setUsers(new ArrayList<>());
        return course;
    }
}
