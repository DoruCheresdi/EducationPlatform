package edplatform.edplat.entities.assignment;

import edplatform.edplat.entities.courses.Course;
import edplatform.edplat.entities.submission.Submission;
import edplatform.edplat.repositories.AssignmentRepository;
import edplatform.edplat.repositories.SubmissionRepository;
import edplatform.edplat.entities.submission.SubmissionService;
import edplatform.edplat.entities.users.User;
import edplatform.edplat.repositories.UserRepository;
import edplatform.edplat.utils.FilePathBuilder;
import edplatform.edplat.utils.FileUploadUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class AssignmentServiceImpl implements AssignmentService {

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FilePathBuilder filePathBuilder;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Override
    public Optional<Assignment> findById(Long id) {
        return assignmentRepository.findById(id);
    }

    @Override
    public void save(Assignment assignment) {
        assignmentRepository.save(assignment);
    }

    @Override
    public void addSubmission(MultipartFile multipartFile, Long assignmentId, User user) throws Exception {
        Optional<Assignment> optionalAssignment = findById(assignmentId);
        Assignment assignment;
        if (optionalAssignment.isPresent()) {
            assignment = optionalAssignment.get();
        } else {
            throw new Error("Error retrieving course from database");
        }
        // create submission:
        Submission submission = new Submission();
        submission.setAssignment(assignment);
        submission.setUser(user);
        String fileName = StringUtils.cleanPath(Objects.requireNonNull(multipartFile.getOriginalFilename()));
        submission.setSubmissionResource(fileName);

        // save submission file:
        String uploadDir = filePathBuilder.getSubmissionFileDirectory(assignment, user);
        FileUploadUtil.saveFile(uploadDir, fileName, multipartFile);

        // save submission to database:
        submissionService.save(submission);
    }

    @Override
    public List<Assignment> findAllAssignmentsWithSubmissionsByCourse(Course course) {
        return assignmentRepository.findAllAssignmentsWithSubmissionsByCourse(course);
    }

    @Override
    public Optional<Assignment> findWithSubmissionsById (Long id) {
        return assignmentRepository.findWithSubmissionsById(id);
    }

    @Override
    @Transactional
    public boolean hasUserSubmitted(Assignment assignment, User user) {
        return assignment.getSubmissions().stream()
                .anyMatch(submission -> submission.getUser().equals(user));
    }

    @Override
    @Transactional
    public Submission getSubmissionForUser(Long assignmentId, Long userId) {
        User user = userRepository.findById(userId).get();
        Assignment assignment = assignmentRepository.findWithSubmissionsById(assignmentId).get();

        return assignment.getSubmissions().stream()
                .filter(submission -> submission.getUser().equals(user))
                .findAny().get();
    }
}
