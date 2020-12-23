package com.ak.collector

import io.circe.generic.extras.Configuration

package object repository {
  object ShapesDerivation {
    implicit val genDevConfig: Configuration =  Configuration.default.withDiscriminator("type")
  }
}
