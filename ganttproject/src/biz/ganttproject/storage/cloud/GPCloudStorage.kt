/*
Copyright 2018 BarD Software s.r.o

This file is part of GanttProject, an opensource project management tool.

GanttProject is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

GanttProject is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with GanttProject.  If not, see <http://www.gnu.org/licenses/>.
*/
package biz.ganttproject.storage.cloud

import biz.ganttproject.FXUtil
import biz.ganttproject.core.option.*
import biz.ganttproject.storage.StorageDialogBuilder
import fi.iki.elonen.NanoHTTPD
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Pane
import net.sourceforge.ganttproject.document.Document
import org.controlsfx.control.HyperlinkLabel
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

val GPCLOUD_LANDING_URL = "https://cloud.ganttproject.biz"
val GPCLOUD_SIGNIN_URL = "https://cloud.ganttproject.biz/__/auth/desktop"
val GPCLOUD_SIGNUP_URL = "https://cloud.ganttproject.biz/__/auth/handler"

/**
 * @author dbarashev@bardsoftware.com
 */
class GPCloudStorage(
    private val mode: StorageDialogBuilder.Mode,
    private val myOptions: GPCloudStorageOptions,
    private val myOpenDocument: Consumer<Document>,
    private val dialogUi: StorageDialogBuilder.DialogUi) : StorageDialogBuilder.Ui {
  private val myPane: BorderPane
//  private val myButtonPane: HBox
//  private val myNextButton: Button


  internal interface PageUi {
    fun createPane(): CompletableFuture<Pane>
  }

  init {
    myPane = BorderPane()
//    myButtonPane = HBox()
//    myButtonPane.styleClass.add("button-pane")
//    myButtonPane.alignment = Pos.CENTER
//    myNextButton = Button("Continue")
//    myButtonPane.children.add(myNextButton)
//    myNextButton.visibleProperty().value = false
//    myPane.bottom = myButtonPane
  }

  override fun getName(): String {
    return "GanttProject Cloud"
  }

  override fun createSettingsUi(): Optional<Pane> {
    return Optional.empty()
  }

  override fun getCategory(): String {
    return "cloud"
  }

  override fun createUi(): Pane {
    return doCreateUi()
  }

  private fun doCreateUi(): Pane {
    val browserPane = GPCloudBrowserPane(this.mode, this.dialogUi)
    val onTokenCallback: AuthTokenCallback = { token, validity, userId ->
      with(GPCloudOptions) {
        this.authToken.value = token
        this.validity.value = validity?.toIntOrNull()
        this.userId.value = userId
      }
      nextPage(browserPane.createStorageUi())
    }

    val signupPane = GPCloudSignupPane(onTokenCallback)
    signupPane.createPane().thenApply { pane ->
      nextPage(pane)
    }
    return myPane
  }

  private fun nextPage(newPage: Pane): Pane {
    FXUtil.transitionCenterPane(myPane, newPage) { dialogUi.resize() }
    return newPage
  }

  companion object {

    internal fun newLabel(key: String, vararg classes: String): Label {
      val label = Label(key)
      label.styleClass.addAll(*classes)
      return label
    }

    internal fun newHyperlink(eventHandler: EventHandler<ActionEvent>, text: String, vararg classes: String): HyperlinkLabel {
      val result = HyperlinkLabel(text)
      result.addEventHandler(ActionEvent.ACTION, eventHandler)
      result.styleClass.addAll(*classes)
      return result
    }
  }
}

// Persistently stored options
object GPCloudOptions {
  val authToken: StringOption = DefaultStringOption("authToken")
  val validity: IntegerOption = DefaultIntegerOption("validity")
  val userId: StringOption = DefaultStringOption("userId")

  val optionGroup: GPOptionGroup = GPOptionGroup("ganttproject-cloud", authToken, validity, userId)
}

// HTTP server for sign in into GP Cloud
typealias AuthTokenCallback = (token: String?, validity: String?, userId: String?) -> Unit

class HttpServerImpl : NanoHTTPD("localhost", 0) {
  var onTokenReceived: AuthTokenCallback? = null

  fun getParam(session: IHTTPSession, key: String): String? {
    val values = session.parameters[key]
    return if (values?.size == 1) values[0] else null
  }

  override fun serve(session: IHTTPSession): Response {
    val args = mutableMapOf<String, String>()
    session.parseBody(args)
    val token = getParam(session, "token")
    val userId = getParam(session, "userId")
    val validity = getParam(session, "validity")

    onTokenReceived?.invoke(token, validity, userId)
    return newFixedLengthResponse("")
  }
}