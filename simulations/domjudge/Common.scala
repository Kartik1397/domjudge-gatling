package domjudge

import io.gatling.core.structure.ChainBuilder
import io.gatling.core.session.Expression
import io.gatling.core.Predef._
import io.gatling.http.Predef._

import java.util.Calendar
import java.text.SimpleDateFormat


// Call this like one of the following:
// exec(User.login(session => session("user").as[String]))
// exec(User.login("username"))
// Password is optional, can be passed in the same way however:
// exec(User.login(session => session("user").as[String]), session => session("pass").as[String]))
// exec(User.login("username", "password"))
object User {
  def login(user: Expression[String], pass: Expression[String] = null): ChainBuilder = {
		val realpass = pass match {	case a:Expression[String] => a;	case _ => user }

    return exec(
        http("Login page get csrf")
        .get("/login")
        .check(
          regex("""<input type="hidden" name="_csrf_token" value="([^"]*)">""")
          .find
          .saveAs("csrftoken")
        ))
      .exec(http("Login Request")
        .post("/login")
        .formParam("_username", session => user(session))
        .formParam("_password", session => realpass(session))
        .formParam("_csrf_token", "${csrftoken}")
      )
	}

  def register(user: Expression[String], pass: Expression[String] = null): ChainBuilder = {
    val realpass = pass match {	case a:Expression[String] => a;	case _ => user }

    return exec(
        http("Registration page get csrf")
        .get("/register")
        .check(
          regex("""<input type="hidden" id="user_registration__token" name="user_registration\[_token\]" value="([^"]*)" """)
          .find
          .saveAs("csrftoken")
        ))
      .exec(http("Register user")
        .post("/register")
        .formParam("user_registration[username]", session => user(session))
        .formParam("user_registration[name]", session => user(session)) // todo make this say "Gatling ###"
        .formParam("user_registration[teamName]", session => user(session))
        .formParam("user_registration[plainPassword][first]", session => realpass(session))
        .formParam("user_registration[plainPassword][second]", session => realpass(session))
        .formParam("user_registration[existingAffiliation]", "1")
        .formParam("user_registration[_token]", "${csrftoken}")
      )
  }
}

object Team {
  def _submit(langid: String, filename: String) =
    exec(
      http("Get submit solution form")
      .get("/team/submit")
      .check(
        regex("""<input type="hidden" id="submit_problem__token" name="submit_problem\[_token\]" value="([^"]*)"""").find
        .saveAs("csrftoken")
        ,
        // get and save the problem id for the "hello" problem
        regex("""<option value="([^"]*)">A - Hello World""")
        .saveAs("problem_id")
    ))
    .exec(http("Submit Solution")
      .post("/team/submit")
      .formParam("submit_problem[_token]","${csrftoken}")
      .formParam("submit_problem[problem]","${problem_id}")
      .formParam("submit_problem[language]", langid)
      .formUpload("submit_problem[code][]", filename)
      .formParam("submit", ""))
  def _submit_with_entrypoint(langid: String, filename: String, entry_point: String) =
    exec(
      http("Get submit solution form")
      .get("/team/submit")
      .check(
        regex("""<input type="hidden" id="submit_problem__token" name="submit_problem\[_token\]" value="([^"]*)"""").find
        .saveAs("csrftoken")
        ,
        // get and save the problem id for the "hello" problem
        regex("""<option value="([^"]*)">hello""")
        .saveAs("problem_id")
    ))
    .exec(http("Submit Solution ${langid}")
      .post("/team/submit")
      .formParam("submit_problem[_token]","${csrftoken}")
      .formParam("submit_problem[problem]","${problem_id}")
      .formParam("submit_problem[language]", langid)
      .formParam("submit_problem[entry_point]", entry_point)
      .formUpload("submit_problem[code][]", filename)
      .formParam("submit", ""))

  val submit_java      = exec(_submit("java",    "test-hello.java"))
  val submit_c         = exec(_submit("c",       "test-hello.c"))
  val submit_hs        = exec(_submit("hs",      "test-hello.hs"))
  val submit_lua       = exec(_submit("lua",     "test-hello.lua"))
  val submit_js        = exec(_submit("js",      "test-hello.js"))
  val submit_csharp    = exec(_submit("csharp",  "test-hello.cs"))
  val submit_py2       = exec(_submit("py2",     "test-hello.py2"))
  val submit_py3       = exec(_submit("py3",     "test-hello.py3"))
  val submit_nonewline = exec(_submit("c",       "test-output-nonewline.c"))
  val submit_kt        = exec(_submit_with_entrypoint("kt",  "test-hello.kt", "Test_helloKt"))
  def requestclarification() =
    exec(
        http("Get request clarification form")
        .get("/team/clarifications/add")
        .check(
          regex("""<input type="hidden" id="team_clarification__token" name="team_clarification\[_token\]" value="([^"]*)"""")
          .saveAs("csrftoken")
          ,
          // get and save the clarification subject id for the "General issue" category
          regex("""<option value="([^"]*-general)">General""").find.saveAs("clarification_subject")
        ))
    .exec(http("Request Clarification")
      .post("/team/clarifications/add")
      .formParam("team_clarification[recipient]", "dummy")
      .formParam("team_clarification[subject]", "${clarification_subject}")
      .formParam("team_clarification[message]", "${user} needs help")
      .formParam("team_clarification[_token]", "${csrftoken}")
      .formParam("submit", "")
    )

  val teampage = exec(http("Team Page").get("/team/")
      .check(
        regex("""(?s)<td class="scoretn.*" title="${user}">.*<a data-ajax-modal href="/team/team/(.*?)">""").find.saveAs("team_id")
      ))
  val teamdetails = exec(http("Team Details").get("/team/team/${team_id}"))
  val teamscoreboard = exec(http("Team Scoreboard").get("/team/scoreboard"))
}

object Spectator {
  def getscoreboard = exec(http("Public Scoreboard Request")
      .get("/public/"))
    // A spectator will check the scoreboard every 30 seconds for a set number of minutes
  def monitor_scoreboard(minutes: Int) = repeat(minutes*2, "n") {
    exec(getscoreboard).pause(30)
  }
}

object Jury {

  // TODO: add jury members browsing around/answering clarifications/etc
  // def view_submissions = ...

  // Sets the session value "contest_id" with the resulting contest id(to be used when uploading a problem)
  def create_contest(shortname: String = "test", name: String = "gatling test contest"): ChainBuilder = {
    val contestformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    return exec(http("Check if contest already exists")
      .get("/jury/contests")
      .check(
        regex("""id=(.*?)">""" + shortname).optional.saveAs("contest_id")
      )
    )
    .doIf(session => session("contest_id").asOption[Any].isDefined) {
      exec(http("Delete existing contest")
        .post("/jury/delete")
        .formParam("table", "contest")
        .formParam("cid", "${contest_id}")
        .formParam("confirm", "Yes I'm sure!")
      )
    }
    .exec(http("Create Contest")
      .post("/jury/contests/add")
      .formParam("data[0][shortname]", shortname)
      .formParam("data[0][name]", name)
      .formParam("data[0][activatetime_string]", "-00:00")
      .formParam("data[0][starttime_string]", contestformat.format(Calendar.getInstance().getTime()) + " America/New_York")
      .formParam("data[0][freezetime_string]", "+99:00")
      .formParam("data[0][endtime_string]", "+99:00")
      .formParam("data[0][unfreezetime_string]", "+99:00")
      .formParam("data[0][deactivatetime_string]", "+99:00")
      .formParam("data[0][process_balloons]", "1")
      .formParam("data[0][public]", "1")
      .formParam("data[0][mapping][1][items]", "")
      .formParam("data[0][enabled]", "1")
      .formParam("problems", "")
      .formParam("data[0][mapping][0][items]", "")
      .formParam("data[0][mapping][0][fk][0]", "cid")
      .formParam("data[0][mapping][0][fk][1]", "probid")
      .formParam("data[0][mapping][0][table]", "contestproblem")
      .formParam("data[0][mapping][1][fk][0]", "cid")
      .formParam("data[0][mapping][1][fk][1]", "teamid")
      .formParam("data[0][mapping][1][table]", "contestteam")
      .formParam("cmd", "add")
      .formParam("table", "contest")
      .formParam("referrer", "")
    ).exec(http("Get Contest")
      .get("/jury/contests")
      .check(
        regex("""id=(.*?)">""" + shortname).find.saveAs("contest_id")
      )
    )
  }

// call as one of the following
// exec(Jury.upload_problem("hello-testcase.zip"))
// exec(Jury.upload_problem("hello-testcase.zip", session => session("contest_id").as[String]))
  def upload_problem(problem_archive: String, contest_id: Expression[String] = null): ChainBuilder = {
    val default:Expression[String] = session => session("contest_id").as[String]
    val cid = contest_id match {	case a:Expression[String] => a;	case _ => default }
    return exec(http("Upload Problem")
        .post("/jury/problem")
        .formParam("contest", session => cid(session))
        .formParam("upload", "Upload")
        .formUpload("problem_archive[]", problem_archive))
  }

  // Takes in a Map("config_item_name" -> "config_value") of new configuration values to apply.
  // All other options will remain unchanged
  def modify_config(config: Map[String,String]) =
    exec(http("Load Current Configuration")
      .get("/jury/config")
      .check(
        regex("""<input.*name="config_(.*?)".*value="(.*?)".*>""").ofType[(String,String)].findAll.saveAs("current_config")
      )
    ).exec(http("Update Configuration")
          .post("/jury/config")
          .formParam("save", "Save")
          .formParamMap(session => session("current_config").as[List[(String,String)]].toMap ++ config))

  // Expects to be called like:
  // Jury.enable_language("C#","csharp",)
  def enable_language(lang: String, langid: String, extensions: List[String]) =
		exec(http("Enable $langid")
			.post("/jury/edit")
			.formParam("keydata[0][langid]", langid)
			.formParam("data[0][name]", lang)
			.formParam("data[0][allow_submit]", "1")
			.formParam("data[0][allow_judge]", "1")
			.formParam("data[0][time_factor]", "1")
			.formParam("data[0][compile_script]", langid)
			.formParam("data[0][extensions]", "[\""+ extensions.mkString("\", \"") + "\"]")
			.formParam("cmd", "edit")
			.formParam("table", "language")
			.formParam("referrer", "languages"))
}
