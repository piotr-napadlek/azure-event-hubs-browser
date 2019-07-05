package io.napadlek.eventhubbrowser.session

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpSession

@RestController
@RequestMapping("/session")
class SessionController {

    @GetMapping
    fun getSessionId(session: HttpSession) = session.id

    @DeleteMapping
    fun invalidateSession(session: HttpSession) {
        session.invalidate()
    }
}
