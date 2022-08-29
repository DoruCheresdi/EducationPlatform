package edplatform.edplat.courses;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface CourseRepository extends CrudRepository<Course, Long> {

     Optional<Course> findById(Long id);
}
