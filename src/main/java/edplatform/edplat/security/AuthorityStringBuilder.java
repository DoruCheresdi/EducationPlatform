package edplatform.edplat.security;

import org.springframework.stereotype.Component;

/**
 * Bean used to build authority strings
 */
@Component
public class AuthorityStringBuilder {

    public String getCourseOwnerAuthority(String Id) {
        return "course-" + Id + "-owner";
    }

    public String getCourseEnrolledAuthority(String Id) {
        return "course-" + Id + "-enrolled";
    }
}
