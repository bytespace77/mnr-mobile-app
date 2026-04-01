package com.safeg

object Constants {

    var base_url: String = "http://192.168.0.214:8080"
//    var base_url: String = "http://educational-advances-grace-journalism.trycloudflare.com"
    var url = java.net.URL(base_url)
    var laravel_url = ""

    var getLocationAccessList = ""
    var getActiveSubLocationAssetList = ""
    var getStateByCountry = ""
    var getCityByState = ""
    var getActiveVehicleType = ""
    var getActiveLicenseList = ""
    var getActiveVaccineList = ""
    var visitorPassRegistration = ""
    var getModuleConfig = ""
    var getActiveCountry = ""
    var visitorPassRegistrationLite = ""
    var getStaffPassByStaffNoOrName = ""
    var getVisitorPassByStaffNoOrName = ""
    var insertVendorPassCard = ""
    var decrypt = ""
    var getCompanies = ""
    var getVisitReasons = ""
    var getdetails = ""
    var uploadVendorPassPhotoMobile = ""
    var visitorTypes = ""

    val date_format = "yyyy-MM-dd"

    init {
        refreshUrls()
    }

    fun refreshUrls() {
        url = java.net.URL(base_url)
        laravel_url = "${url.protocol}://${url.host}"
//        laravel_url = "http://concluded-turtle-buttons-base.trycloudflare.com"
        getLocationAccessList = "$base_url/api/admin/locationAccess/active"
        getActiveSubLocationAssetList = "$base_url/api/admin/subLocationAccess/active"
        getStateByCountry = "$base_url/api/admin/state/country/"
        getCityByState = "$base_url/api/admin/city/state/"
        getActiveVehicleType = "$base_url/api/admin/vinType/active/all"
        getActiveLicenseList = "$base_url/api/admin/licenseClass/active"
        getActiveVaccineList = "$base_url/api/admin/vaccineType/active"
        visitorPassRegistration = "$base_url/api/vendorpass/doVisitorPassReqMobile"
        visitorPassRegistrationLite = "$base_url/api/vendorpass/doVisitorPassReqMobile"
        getModuleConfig = "$base_url/api/admin/moduleConfig/getByProject"
        getActiveCountry = "$base_url/api/admin/country/active"
        getStaffPassByStaffNoOrName = "$base_url/api/user/getStaffPassByStaffNoOrName"
        getVisitorPassByStaffNoOrName = "$base_url/api/user/getVisitorPassByStaffNoOrName"
        insertVendorPassCard = "$base_url/api/vendorpass/insertVendorPassCard"
        decrypt = "$laravel_url/decrypt"
        visitorTypes = "$laravel_url/api/visitor-types"
        getCompanies = "$base_url/api/admin/campanies/all"
        getVisitReasons = "$base_url/api/vendorpass/getVisitReasonList?page=0&pageSize=20"
        getdetails = "$base_url/api/vendorpass/checkICExist"
        uploadVendorPassPhotoMobile = "$base_url/api/vendorpass/uploadVendorPassPhotoMobile"
    }
}
