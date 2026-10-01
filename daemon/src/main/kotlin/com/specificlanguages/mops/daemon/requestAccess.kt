package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*

// Checking invokes shared typesystem/checker services; rendering uses the EDT. Both retain exclusive admission.
internal val DaemonRequest.parallelRead: Boolean
    get() = when (this) {
        is ModelGetNodeRequest, is MpsListRequest,
        is FindUsagesRequest, is FindInstancesRequest, is FindByNameRequest, is FindNodeByIdRequest,
        is DiagnoseModulesRequest, is DiagnoseModuleRequest, is CodeCatalogRequest -> true
        else -> false
    }

internal val DaemonRequest.savesProject: Boolean
    get() = when (this) {
        is ModelGetNodeRequest, is MpsListRequest, is ModelRenderNodeRequest,
        is ModelCheckRequest, is ProjectCheckRequest, is ModuleCheckRequest,
        is FindUsagesRequest, is FindInstancesRequest, is FindByNameRequest, is FindNodeByIdRequest,
        is DiagnoseModulesRequest, is DiagnoseModuleRequest, is CodeCatalogRequest -> false
        else -> true
    }
