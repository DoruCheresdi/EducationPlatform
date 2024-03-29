package edplatform.edplat.tests;

import edplatform.edplat.entities.authority.AuthorityService;
import edplatform.edplat.entities.users.User;
import edplatform.edplat.repositories.UserRepository;
import edplatform.edplat.security.AuthorityStringBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
public class AuthorityServiceTests {

    @Autowired
    private AuthorityService authorityService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityStringBuilder authorityStringBuilder;

    @Test
    @Transactional
    void shouldGiveRightAuthorityToUser() {
        User user = new User();
        String email = "authorityTest@test.com";
        String testFirstName = "testFirstName";
        String testLastName = "testLastName";
        user.setEmail(email);
        user.setFirstName(testFirstName);
        user.setLastName(testLastName);
        user.setPassword("test");
        userRepository.save(user);

        // get user from database and make sure it is the right one:
        User foundUser = userRepository.findByEmail(email).get();
        foundUser.setAuthorities(new HashSet<>());
        assertThat(foundUser.getFirstName()).isEqualTo(testFirstName);

        authorityService.giveAuthorityToUser(foundUser, authorityStringBuilder.getCourseOwnerAuthority("4"));

        // retrieve the persisted user again:
        foundUser = userRepository.findByEmail(email).get();
        assertThat(foundUser.getAuthorities().size()).isEqualTo(1);
        assertThat(foundUser.getAuthorities().iterator().next().getAuthority())
                .isEqualTo(authorityStringBuilder.getCourseOwnerAuthority("4"));
    }
}
