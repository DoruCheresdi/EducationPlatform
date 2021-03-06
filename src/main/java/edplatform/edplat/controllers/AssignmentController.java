package edplatform.edplat.controllers;

import edplatform.edplat.assignment.Assignment;
import edplatform.edplat.assignment.AssignmentRepository;
import edplatform.edplat.courses.Course;
import edplatform.edplat.courses.CourseRepository;
import edplatform.edplat.submission.Submission;
import edplatform.edplat.submission.SubmissionRepository;
import edplatform.edplat.users.User;
import edplatform.edplat.users.UserRepository;
import edplatform.edplat.utils.FileUploadUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.Optional;

@Controller
public class AssignmentController {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    AssignmentRepository assignmentRepository;

    @Autowired
    SubmissionRepository submissionRepository;

    @GetMapping("/assignment/new")
    public String showNewAssignmentView(@RequestParam Long courseId,
                                        Model model) {

        model.addAttribute("courseId", courseId);

        model.addAttribute("assignment", new Assignment());

        return "add_assignment";
    }

    @PostMapping("/assignment/new")
    public RedirectView createAssignment(Assignment assignment,
                                         @RequestParam Long courseId) {

        Optional<Course> optionalCourse = courseRepository.findById(courseId);
        Course course;
        if (optionalCourse.isPresent()) {
            course = optionalCourse.get();
        } else {
            // TODO add error page:
            return new RedirectView("course/courses");
        }

        assignment.setCourse(course);
        assignmentRepository.save(assignment);

        return new RedirectView("/course?id=" + assignment.getCourse().getId());
    }

    @GetMapping("/assignment/submission/new")
    public String showNewSubmissionView(@RequestParam Long assignmentId,
                                        Model model) {

        model.addAttribute("assignmentId", assignmentId);

        return "add_submission";
    }

    @PostMapping("/assignment/submission/new")
    public RedirectView createSubmission(@RequestParam("resource") MultipartFile multipartFile,
                                         @RequestParam Long assignmentId,
                                         Authentication authentication) throws IOException {

        Optional<Assignment> optionalAssignment = assignmentRepository.findById(assignmentId);
        Assignment assignment;
        if (optionalAssignment.isPresent()) {
            assignment = optionalAssignment.get();
        } else {
            // TODO add error page:
            return new RedirectView("course/courses");
        }

        // save image name to database:
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername());

        String fileName = StringUtils.cleanPath(multipartFile.getOriginalFilename());

        // save image name to database:
        Submission submission = new Submission();
        submission.setAssignment(assignment);
        submission.setUser(user);
        submission.setSubmissionResource(fileName);

        String uploadDir = "submissions/" + assignment.getId() + "-" + user.getId();

        FileUploadUtil.saveFile(uploadDir, fileName, multipartFile);

        submissionRepository.save(submission);

        return new RedirectView("/course?id=" + assignment.getCourse().getId());
    }
}
