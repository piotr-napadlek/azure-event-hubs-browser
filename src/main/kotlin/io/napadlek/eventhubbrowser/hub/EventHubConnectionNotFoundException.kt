package io.napadlek.eventhubbrowser.hub

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class EventHubConnectionNotFoundException(id: String) : ResponseStatusException(HttpStatus.NOT_FOUND, "Event hub connection with id $id was not found")
