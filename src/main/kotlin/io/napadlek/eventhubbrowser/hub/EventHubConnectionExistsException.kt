package io.napadlek.eventhubbrowser.hub

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class EventHubConnectionExistsException(id: String) : ResponseStatusException(HttpStatus.CONFLICT, "Event hub connection with id $id already exists")