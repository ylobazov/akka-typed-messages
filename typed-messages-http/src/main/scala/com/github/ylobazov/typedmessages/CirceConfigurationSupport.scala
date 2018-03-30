package com.github.ylobazov.typedmessages

import io.circe.Printer
import io.circe.generic.extras.Configuration

trait CirceConfigurationSupport {
  implicit val circeConfiguration: Configuration
  implicit val printer: Printer
}

