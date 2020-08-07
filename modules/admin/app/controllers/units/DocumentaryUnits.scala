package controllers.units

import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import forms._
import models.{EntityType, _}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.data.DataHelpers
import services.ingest.{ImportLogService, IngestService}
import services.search._
import services.storage.FileStorage
import utils.{PageParams, RangeParams}
import views.Helpers

import javax.inject._
import scala.concurrent.Future



@Singleton
case class DocumentaryUnits @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  importLogs: ImportLogService,
  @Named("dam") damStorage: FileStorage,
) extends AdminController
  with Read[DocumentaryUnit]
  with Visibility[DocumentaryUnit]
  with Creator[DocumentaryUnit, DocumentaryUnit]
  with Update[DocumentaryUnit]
  with Delete[DocumentaryUnit]
  with DeleteChildren[DocumentaryUnit, DocumentaryUnit]
  with ScopePermissions[DocumentaryUnit]
  with Annotate[DocumentaryUnit]
  with Linking[DocumentaryUnit]
  with Descriptions[DocumentaryUnit]
  with AccessPoints[DocumentaryUnit]
  with Search {

  // Documentary unit facets
  import SearchConstants._

  override protected val targetContentTypes = Seq(ContentTypes.DocumentaryUnit)
  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = IS_PARENT,
        name = Messages("facet.parent"),
        param = "parent",
        render = s => Messages("facet.parent." + s),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("documentaryUnit." + LANGUAGE_CODE),
        param = "lang",
        render = Helpers.languageCodeToName
      ),
      FieldFacetClass(
        key = CREATION_PROCESS,
        name = Messages("facet.source"),
        param = "source",
        render = s => Messages("facet.source." + s),
        sort = FacetSort.Name,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key = COUNTRY_CODE,
        name = Messages("repository." + COUNTRY_CODE),
        param = "country",
        render = (s: String) => Helpers.countryCodeToName(s),
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key = HOLDER_NAME,
        name = Messages("documentaryUnit.heldBy"),
        param = "holder",
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key = RESTRICTED_FIELD,
        name = Messages("facet.restricted"),
        param = "restricted",
        render = s => Messages("facet.restricted." + s),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
    )
  }
  private val formConfig: FormConfigBuilder = FormConfigBuilder(EntityType.DocumentaryUnit, config)
  private val form = models.DocumentaryUnit.form
  private val childForm = models.DocumentaryUnit.form
  private val descriptionForm = models.DocumentaryUnitDescription.form

  private val docRoutes = controllers.units.routes.DocumentaryUnits
  private val renameForm = Form(single(IDENTIFIER -> nonEmptyText))

  def search(params: SearchParams, paging: PageParams): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    // What filters we gonna use? How about, only list stuff here that
    // has no parent items - UNLESS there's a query, in which case we're
    // going to peer INSIDE items... dodgy logic, maybe...
    val filters = if (!hasActiveQuery(request))
      Map(SearchConstants.TOP_LEVEL -> true) else Map.empty[String, Any]

    findType[DocumentaryUnit](params, paging, filters = filters, facetBuilder = entityFacets).map { result =>
      Ok(views.html.admin.documentaryUnit.search(
        result,
        docRoutes.search()))
    }
  }

  def get(id: String, dlid: Option[String], params: SearchParams, paging: PageParams): Action[AnyContent] = ItemMetaAction(id).async { implicit request =>
    findType[DocumentaryUnit](params, paging, filters = Map(SearchConstants.PARENT_ID -> request.item.id),
      facetBuilder = entityFacets, sort = SearchSort.Id).map { result =>
      if (isAjax) Ok(views.html.admin.search.inlineItemList(result = result))
        .withHeaders("more" -> result.page.hasMore.toString)
      else Ok(views.html.admin.documentaryUnit.show(request.item, result,
        docRoutes.get(id), request.annotations, request.links, dlid))
          .withPreferences(preferences.withRecentItem(id))
    }
  }

  def history(id: String, range: RangeParams): Action[AnyContent] = ItemHistoryAction(id, range).async { implicit request =>
    import scala.concurrent.duration._
    for (fileHandles <- importLogs.getHandles(id)) yield {
      val eventHandles: Map[String, Seq[(String, java.net.URI)]] = fileHandles
        .groupBy(_.eventId)
        .view
        .mapValues(_.map(f => (f.key, damStorage.uri(f.key, duration = 2.hours, versionId = f.versionId))))
        .toMap

      Ok(views.html.admin.documentaryUnit.eventList(
        request.item, request.page, request.params, eventHandles))
    }
  }

  def list(paging: PageParams): Action[AnyContent] = ItemPageAction(paging).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.list(request.page, request.params))
  }

  def update(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.edit(
      request.item, form.fill(request.item.data), formConfig.forUpdate, docRoutes.updatePost(id)))
  }

  def updatePost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.documentaryUnit.edit(request.item,
          errorForm, formConfig.forUpdate, docRoutes.updatePost(id)))
      case Right(item) => Redirect(docRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def rename(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.rename(request.item, renameForm, docRoutes.renamePost(id)))
  }

  def renamePost(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    val boundForm = renameForm.bindFromRequest()
    boundForm.fold(
      errForm => Future.successful(
        BadRequest(views.html.admin.documentaryUnit.rename(request.item, errForm, docRoutes.renamePost(id)))),
      local => userDataApi.rename[DocumentaryUnit](id, local, getLogMessage, check = true).flatMap { collisions =>
        if (collisions.nonEmpty) {
          Future.successful(BadRequest(views.html.admin.documentaryUnit.rename(
            request.item, boundForm, docRoutes.renamePost(id), collisions)))
        } else userDataApi.rename[DocumentaryUnit](id, local, getLogMessage).flatMap { mappings =>
          val newId = mappings.headOption.map(_._2).getOrElse(id)
          val portalRoutes = controllers.portal.routes.DocumentaryUnits
          val redirectUrls = mappings.flatMap { case (from, to) =>
            Seq(
              portalRoutes.browse(from).url -> portalRoutes.browse(to).url,
              portalRoutes.search(from).url -> portalRoutes.search(to).url,
              docRoutes.get(from).url -> docRoutes.get(to).url
            )
          }
          val relocateF = appComponents.pageRelocator.addMoved(redirectUrls)
          val importRefsF = importLogs.updateHandles(mappings)
          for (count <- relocateF; _ <- importRefsF) yield {
            Redirect(docRoutes.get(newId))
              .flashing("success" -> Messages("item.rename.confirmation", count))
          }
        }
      }
    )
  }

  def createDoc(id: String): Action[AnyContent] = NewChildAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.create(
      request.item, childForm, formConfig.forCreate, visibilityForm.fill(request.item.accessors.map(_.id)),
      request.usersAndGroups, docRoutes.createDocPost(id)))
  }

  def createDocPost(id: String): Action[AnyContent] = CreateChildAction(id, childForm).apply { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm, usersAndGroups)) =>
        BadRequest(views.html.admin.documentaryUnit.create(request.item,
          errorForm, formConfig.forCreate, accForm, usersAndGroups,
          docRoutes.createDocPost(id)))
      case Right(doc) => Redirect(docRoutes.get(doc.id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def createDescription(id: String): Action[AnyContent] =
    WithItemPermissionAction(id, PermissionType.Update).apply { implicit request =>
      Ok(views.html.admin.documentaryUnit.createDescription(request.item,
        form.fill(request.item.data), formConfig.forCreate, docRoutes.createDescriptionPost(id)))
    }

  def createDescriptionPost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.documentaryUnit.createDescription(request.item,
          errorForm, formConfig.forCreate, docRoutes.createDescriptionPost(id)))
      case Right(_) => Redirect(docRoutes.get(id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def updateDescription(id: String, did: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.editDescription(request.item,
      form.fill(request.item.data), formConfig.forUpdate, did, docRoutes.updateDescriptionPost(id, did)))
  }

  def updateDescriptionPost(id: String, did: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.documentaryUnit.editDescription(request.item,
          errorForm, formConfig.forUpdate, did, docRoutes.updateDescriptionPost(id, did)))
      case Right(_) => Redirect(docRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def deleteDescription(id: String, did: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.deleteDescription(request.item, form.fill(request.item.data),
      did, docRoutes.deleteDescriptionPost(id, did)))
  }

  def deleteDescriptionPost(id: String, did: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.documentaryUnit.deleteDescription(request.item,
          errorForm, did, docRoutes.deleteDescriptionPost(id, did)))
      case Right(_) => Redirect(docRoutes.get(id))
        .flashing("success" -> "item.delete.confirmation")

    }
  }

  def delete(id: String, paging: PageParams): Action[AnyContent] = CheckDeleteAction(id).async { implicit request =>
    userDataApi.children[DocumentaryUnit, DocumentaryUnit](id, paging).map { children =>
      Ok(views.html.admin.deleteParent(
        request.item, children,
        docRoutes.deletePost(id, goToId = request.item.parent.map(_.id)),
        cancel = docRoutes.get(id),
        deleteChild = cid => docRoutes.delete(cid),
        deleteAll = Some(docRoutes.deleteContents(id)),
        breadcrumbs = views.html.admin.documentaryUnit.breadcrumbs(request.item)))
    }
  }

  def deletePost(id: String, goToId: Option[String]): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(goToId.map(p => docRoutes.get(p)).getOrElse(docRoutes.search()))
      .flashing("success" -> "item.delete.confirmation")
  }

  def deleteContents(id: String, params: PageParams): Action[AnyContent] = CheckDeleteChildrenAction(id, params).apply { implicit request =>
    Ok(views.html.admin.deleteChildren(
      request.item,
      request.children,
      DeleteChildrenOptions.form,
      docRoutes.deleteContentsPost(id),
      cancel = docRoutes.get(id),
      breadcrumbs = views.html.admin.documentaryUnit.breadcrumbs(request.item)))
  }

  def deleteContentsPost(id: String, params: PageParams): Action[AnyContent] = DeleteChildrenAction(id, params).apply { implicit request =>
    request.formOrIds match {
      case Left((errForm, children)) =>
        BadRequest(views.html.admin.deleteChildren(
          request.item,
          children,
          errForm,
          docRoutes.deleteContentsPost(id),
          cancel = docRoutes.get(id),
          breadcrumbs = views.html.admin.documentaryUnit.breadcrumbs(request.item)))
      case Right(ids) =>
        Redirect(docRoutes.get(id))
          .flashing("success" -> Messages("item.deleteChildren.confirmation", ids.size))
    }
  }
  def visibility(id: String): Action[AnyContent] = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      visibilityForm.fill(request.item.accessors.map(_.id)),
      request.usersAndGroups, docRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String): Action[AnyContent] = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(docRoutes.get(id))
      .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String, paging: PageParams, scopePaging: PageParams): Action[AnyContent] =
    ScopePermissionGrantAction(id, paging, scopePaging).apply { implicit request =>
      Ok(views.html.admin.permissions.manageScopedPermissions(
        request.item, request.permissionGrants, request.scopePermissionGrants,
        docRoutes.addItemPermissions(id),
        docRoutes.addScopedPermissions(id)))
    }

  def addItemPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.usersAndGroups,
      docRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.usersAndGroups,
      docRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] =
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        docRoutes.setItemPermissionsPost(id, userType, userId)))
    }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] =
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(docRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] =
    CheckUpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionScope(
        request.item, request.accessor, request.scopePermissions, targetContentTypes,
        docRoutes.setScopedPermissionsPost(id, userType, userId)))
    }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] =
    UpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(docRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }

  def linkTo(id: String): Action[AnyContent] =
    WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
      Ok(views.html.admin.documentaryUnit.linkTo(request.item))
    }

  def linkAnnotateSelect(id: String, toType: EntityType.Value, params: SearchParams, paging: PageParams): Action[AnyContent] =
    LinkSelectAction(id, toType, params, paging).apply { implicit request =>
      Ok(views.html.admin.link.linkSourceList(
        request.item, request.searchResult, request.entityType,
        docRoutes.linkAnnotateSelect(id, toType),
        (other, copy) => docRoutes.linkAnnotate(id, toType, other, copy)))
    }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String, copy: Boolean): Action[AnyContent] =
    LinkAction(id, toType, to).apply { implicit request =>
      Ok(views.html.admin.link.create(request.from, request.to,
        Link.formWithCopyOptions(copy, request.from, request.to),
          docRoutes.linkAnnotatePost(id, toType, to, copy), copy))
    }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String, copy: Boolean): Action[AnyContent] =
    CreateLinkAction(id, toType, to, directional = copy).apply { implicit request =>
      request.formOrLink match {
        case Left((target, errorForm)) =>
          BadRequest(views.html.admin.link.create(request.from, target,
            errorForm, docRoutes.linkAnnotatePost(id, toType, to, copy), copy))
        case Right(_) =>
          Redirect(docRoutes.get(id))
            .flashing("success" -> "item.update.confirmation")
      }
    }

  def manageAccessPoints(id: String, descriptionId: String): Action[AnyContent] =
    WithDescriptionAction(id, descriptionId).apply { implicit request =>
      // Holder IDs for vocabularies and authoritative sets to which
      // access point suggestions will be constrainted. If this is empty
      // all available vocabs/auth sets will be used.
      val holders = config
        .getOptional[Seq[String]]("ehri.admin.accessPoints.holders")
        .getOrElse(Seq.empty)
      Ok(views.html.admin.documentaryUnit.editAccessPoints(request.item,
        request.description, holderIds = holders))
    }

  def ingest(id: String): Action[AnyContent] = (AdminAction andThen ItemPermissionAction(id)).apply { implicit request =>
    request.item.holder.map { scope =>
      val dataType = IngestService.IngestDataType.EadSync
      Ok(views.html.admin.tools.ingest(scope, Some(request.item), IngestParams.ingestForm, dataType,
        controllers.admin.routes.Ingest.ingestPost(ContentTypes.DocumentaryUnit,
          scope.id, dataType, Some(id)), sync = true))
    }.getOrElse(InternalServerError(views.html.errors.fatalError()))
  }
}


