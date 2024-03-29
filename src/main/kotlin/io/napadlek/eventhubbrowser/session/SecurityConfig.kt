package io.napadlek.eventhubbrowser.session

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

@Configuration
@EnableWebSecurity
class SecurityConfig : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity?) {
        http?.let {
            it.csrf().disable().authorizeRequests().anyRequest().permitAll().and().anonymous()
        }
    }
}
