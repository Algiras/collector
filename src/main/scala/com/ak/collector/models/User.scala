package com.ak.collector.models

import java.util.UUID
import com.ak.collector.repository.UserRepository

case class User(id: UUID, email: String, password: String, role: UserRepository.Role)

