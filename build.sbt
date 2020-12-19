val Http4sVersion     = "0.21.11"
val CirceVersion      = "0.13.0"
val Specs2Version     = "4.10.5"
val LogbackVersion    = "1.2.3"
val PueConfigVersion  = "0.14.0"
val FlywayVersion     = "7.3.1"
val DoobieVersion     = "0.9.0"
val CryptoBitsVersion = "1.3"

enablePlugins(JavaAppPackaging)

lazy val root = (project in file("."))
  .settings(
    organization := "com.ak",
    name := "collector",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.3",
    libraryDependencies ++= Seq(
      "org.http4s"            %% "http4s-blaze-server"    % Http4sVersion,
      "org.http4s"            %% "http4s-blaze-client"    % Http4sVersion,
      "org.http4s"            %% "http4s-circe"           % Http4sVersion,
      "org.http4s"            %% "http4s-dsl"             % Http4sVersion,
      "org.tpolecat"          %% "doobie-core"            % DoobieVersion,
      "org.tpolecat"          %% "doobie-postgres"        % DoobieVersion,
      "org.tpolecat"          %% "doobie-hikari"          % DoobieVersion,
      "org.flywaydb"           % "flyway-core"            % FlywayVersion,
      "io.circe"              %% "circe-generic"          % CirceVersion,
      "com.github.pureconfig" %% "pureconfig"             % PueConfigVersion,
      "com.github.pureconfig" %% "pureconfig-cats-effect" % PueConfigVersion,
      "org.reactormonk"       %% "cryptobits"             % CryptoBitsVersion,
      "org.specs2"            %% "specs2-core"            % Specs2Version % "test",
      "ch.qos.logback"         % "logback-classic"        % LogbackVersion
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
  )
