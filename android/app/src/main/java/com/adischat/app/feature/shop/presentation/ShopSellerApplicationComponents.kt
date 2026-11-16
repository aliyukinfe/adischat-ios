package com.adischat.app.feature.shop.presentation

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.feature.shop.data.ShopProtectedState
import com.adischat.app.feature.shop.data.ShopSellerApplicationDto
import com.adischat.app.feature.shop.data.ShopSellerApplicationDocumentDto
import com.adischat.app.feature.shop.data.ShopSellerApplicationRequest
import com.adischat.app.ui.theme.AdisGebeyaColors

private val FormSurface = AdisGebeyaColors.Surface
private val FormSoft = AdisGebeyaColors.SecondarySurface
private val FormInk = AdisGebeyaColors.PrimaryText
private val FormMuted = AdisGebeyaColors.SecondaryText
private val FormGreen = AdisGebeyaColors.Primary
private val FormOrange = AdisGebeyaColors.AccentOrange
private val FormOrangeDark = AdisGebeyaColors.AccentOrangeDark
private val FormLine = AdisGebeyaColors.Divider
private val FormBorder = AdisGebeyaColors.Border
private val FormRed = AdisGebeyaColors.Error
private val FormAmberSoft = AdisGebeyaColors.SoftOrange
private val FormOnColor = AdisGebeyaColors.OnColor

private data class SellerApplicationFormState(
    val proposedStoreName: String = "",
    val legalName: String = "",
    val businessType: String = "",
    val registration: String = "",
    val tax: String = "",
    val categories: String = "",
    val description: String = "",
    val tagline: String = "",
    val ownerName: String = "",
    val contactName: String = "",
    val phone: String = "",
    val email: String = "",
    val supportPhone: String = "",
    val supportEmail: String = "",
    val country: String = "Ethiopia",
    val region: String = SELLER_REGION_ADDIS_ABABA,
    val city: String = "Addis Ababa",
    val district: String = "",
    val address: String = "",
    val locationNotes: String = "",
    val serviceArea: String = "",
    val fulfillment: String = "",
    val handling: String = "",
    val returnPolicy: String = "",
    val exchangePolicy: String = "",
    val shippingPolicy: String = "",
    val warrantyPolicy: String = "",
    val supportPolicy: String = "",
    val terms: Boolean = false,
    val privacy: Boolean = false,
    val sellerPolicy: Boolean = false,
    val declaration: Boolean = false,
) {
    fun toRequest(step: Int): ShopSellerApplicationRequest {
        val categoryList: List<String> = categories.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return ShopSellerApplicationRequest(
            storeName = proposedStoreName,
            proposedStoreName = proposedStoreName,
            legalBusinessName = legalName,
            businessType = businessType,
            businessRegistrationNumber = registration,
            taxIdentifier = tax,
            storeDescription = description,
            expectedProductCategories = categoryList,
            shortTagline = tagline,
            ownerFullName = ownerName,
            contactPersonName = contactName,
            phone = phone,
            email = email,
            supportPhone = supportPhone,
            supportEmail = supportEmail,
            country = country,
            regionState = region,
            city = city,
            district = district,
            addressLine = address,
            locationNotes = locationNotes,
            fulfillmentMethod = fulfillment,
            serviceArea = serviceArea,
            estimatedHandlingTime = handling,
            returnPolicy = returnPolicy,
            exchangePolicy = exchangePolicy,
            shippingPolicy = shippingPolicy,
            warrantyPolicy = warrantyPolicy,
            customerServicePolicy = supportPolicy,
            termsAccepted = terms,
            privacyAcknowledged = privacy,
            sellerPolicyAccepted = sellerPolicy,
            declarationOfAccuracy = declaration,
            currentStep = step,
        )
    }

    companion object {
        fun from(application: ShopSellerApplicationDto?): SellerApplicationFormState = SellerApplicationFormState(
            proposedStoreName = application?.proposedStoreName.orEmpty(),
            legalName = application?.legalBusinessName.orEmpty(),
            businessType = application?.businessType?.takeIf { value ->
                SELLER_BUSINESS_TYPES.any { (code, _) -> code == value }
            }.orEmpty(),
            registration = application?.businessRegistrationNumber.orEmpty(),
            tax = application?.taxIdentifier.orEmpty(),
            categories = application?.expectedProductCategories?.joinToString(", ").orEmpty(),
            description = application?.storeDescription.orEmpty(),
            tagline = application?.shortTagline.orEmpty(),
            ownerName = application?.ownerFullName.orEmpty(),
            contactName = application?.contactPersonName.orEmpty(),
            phone = application?.phone.orEmpty(),
            email = application?.email.orEmpty(),
            supportPhone = application?.supportPhone.orEmpty(),
            supportEmail = application?.supportEmail.orEmpty(),
            country = application?.country ?: "Ethiopia",
            region = SELLER_REGION_ADDIS_ABABA,
            city = "Addis Ababa",
            district = application?.district?.takeIf(SELLER_ADDIS_ABABA_SUBCITIES::contains).orEmpty(),
            address = application?.addressLine.orEmpty(),
            locationNotes = application?.locationNotes.orEmpty(),
            serviceArea = application?.serviceArea.orEmpty(),
            fulfillment = application?.fulfillmentMethod.orEmpty(),
            handling = application?.estimatedHandlingTime.orEmpty(),
            returnPolicy = application?.returnPolicy.orEmpty(),
            exchangePolicy = application?.exchangePolicy.orEmpty(),
            shippingPolicy = application?.shippingPolicy.orEmpty(),
            warrantyPolicy = application?.warrantyPolicy.orEmpty(),
            supportPolicy = application?.customerServicePolicy.orEmpty(),
            terms = application?.termsAccepted == true,
            privacy = application?.privacyAcknowledged == true,
            sellerPolicy = application?.sellerPolicyAccepted == true,
            declaration = application?.declarationOfAccuracy == true,
        )
    }
}

@Composable
internal fun SellerApplicantScreen(
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    notificationUnreadCount: Int,
    onOpenNotifications: () -> Unit,
    onSaveDraft: (ShopSellerApplicationRequest) -> Unit,
    onSubmit: (ShopSellerApplicationRequest) -> Unit,
    onRespondInfoRequest: (String) -> Unit,
    onUploadDocument: (String, ByteArray, String, String) -> Unit,
    onRemoveDocument: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(AdisGebeyaColors.Background).statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = FormInk)
                }
                Column(Modifier.weight(1f)) {
                    Text("Apply to sell", color = FormInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("AdisGebeya Seller Center", color = FormMuted, fontSize = 12.sp)
                }
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Default.Notifications, contentDescription = "Seller notifications${if (notificationUnreadCount > 0) ", $notificationUnreadCount unread" else ""}", tint = FormInk)
                }
            }
        }
        if (protectedState.loading && protectedState.sellerApplication == null) {
            item {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = FormGreen)
                }
            }
        } else {
            protectedState.error?.let { message -> item { SellerEmptyCard(message) } }
            item {
                SellerApplicationFormCard(
                    protectedState = protectedState,
                    onSaveDraft = onSaveDraft,
                    onSubmit = onSubmit,
                    onRespondInfoRequest = onRespondInfoRequest,
                    onUploadDocument = onUploadDocument,
                    onRemoveDocument = onRemoveDocument,
                )
            }
        }
    }
}

@Composable
internal fun SellerApplicationFormCard(
    protectedState: ShopProtectedState,
    onSaveDraft: (ShopSellerApplicationRequest) -> Unit,
    onSubmit: (ShopSellerApplicationRequest) -> Unit,
    onRespondInfoRequest: (String) -> Unit,
    onUploadDocument: (String, ByteArray, String, String) -> Unit,
    onRemoveDocument: (String) -> Unit,
) {
    val status = protectedState.sellerStatus
    val app = protectedState.sellerApplication
    var step by remember(app?.id) { mutableStateOf((app?.currentStep ?: 1).coerceIn(1, 7)) }
    var form by remember(app?.id) { mutableStateOf(SellerApplicationFormState.from(app)) }
    var infoResponse by remember(app?.id) { mutableStateOf("") }
    var validationError by remember(app?.id) { mutableStateOf<String?>(null) }
    val busy = protectedState.busyIds.contains("seller-application")
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(FormOnColor), border = BorderStroke(1.dp, FormLine)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Seller status", color = FormInk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text(formBackendLabel(status), color = sellerStatusColor(status), fontWeight = FontWeight.SemiBold)
            when (status) {
                "not_started", "draft", "information_required", "rejected" -> {
                    Text("Complete the seven-step application. Drafts are saved to your secure Shop profile.", color = FormMuted, lineHeight = 20.sp)
                    if (status == "information_required") {
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(FormAmberSoft), border = BorderStroke(1.dp, FormOrange.copy(alpha = .25f))) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("More information requested", color = FormOrangeDark, fontWeight = FontWeight.Black)
                                Text(app?.informationRequestMessage ?: "Admin requested more information.", color = FormInk)
                                SellerField(infoResponse, { infoResponse = it }, "Response to Admin", 2)
                                OutlinedButton(onClick = { onRespondInfoRequest(infoResponse) }, enabled = !busy && infoResponse.trim().length >= 10, shape = RoundedCornerShape(14.dp)) { Text("Send response") }
                            }
                        }
                    }
                    SellerApplicationProgress(
                        step = step,
                        onStep = { requestedStep ->
                            if (requestedStep <= step) {
                                step = requestedStep
                                validationError = null
                            }
                        },
                    )
                    when (step) {
                        1 -> SellerBusinessStep(
                            form.proposedStoreName, { form = form.copy(proposedStoreName = it) }, form.legalName, { form = form.copy(legalName = it) }, form.businessType, { form = form.copy(businessType = it) },
                            form.registration, { form = form.copy(registration = it) }, form.tax, { form = form.copy(tax = it) }, form.categories, { form = form.copy(categories = it) }, form.description, { form = form.copy(description = it) }, form.tagline, { form = form.copy(tagline = it) },
                        )
                        2 -> SellerOwnerStep(form.ownerName, { form = form.copy(ownerName = it) }, form.contactName, { form = form.copy(contactName = it) }, form.phone, { form = form.copy(phone = it) }, form.email, { form = form.copy(email = it) }, form.supportPhone, { form = form.copy(supportPhone = it) }, form.supportEmail, { form = form.copy(supportEmail = it) })
                        3 -> SellerAddressStep(form.district, { form = form.copy(district = it) }, form.address, { form = form.copy(address = it) }, form.locationNotes, { form = form.copy(locationNotes = it) })
                        4 -> SellerStoreStep(form.description, { form = form.copy(description = it) }, form.serviceArea, { form = form.copy(serviceArea = it) }, form.fulfillment, { form = form.copy(fulfillment = it) }, form.handling, { form = form.copy(handling = it) })
                        5 -> SellerPoliciesStep(form.returnPolicy, { form = form.copy(returnPolicy = it) }, form.exchangePolicy, { form = form.copy(exchangePolicy = it) }, form.shippingPolicy, { form = form.copy(shippingPolicy = it) }, form.warrantyPolicy, { form = form.copy(warrantyPolicy = it) }, form.supportPolicy, { form = form.copy(supportPolicy = it) })
                        6 -> SellerDocumentStatusList(
                            applicationExists = app?.id?.isNotBlank() == true,
                            documents = app?.documents.orEmpty(),
                            busy = protectedState.busyIds.contains("seller-application-document"),
                            onUploadDocument = onUploadDocument,
                            onRemoveDocument = onRemoveDocument,
                        )
                        7 -> SellerReviewStep(form.proposedStoreName, form.ownerName, form.phone, form.email, form.city, form.returnPolicy, form.exchangePolicy, form.shippingPolicy, form.terms, { form = form.copy(terms = it) }, form.privacy, { form = form.copy(privacy = it) }, form.sellerPolicy, { form = form.copy(sellerPolicy = it) }, form.declaration, { form = form.copy(declaration = it) })
                    }
                    validationError?.let { Text(it, color = FormRed, fontWeight = FontWeight.Medium) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (step > 1) step -= 1 }, enabled = step > 1 && !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Back") }
                        Button(
                            onClick = {
                                val issue = sellerApplicationStepIssue(step, form)
                                if (issue != null) {
                                    validationError = issue
                                } else {
                                    validationError = null
                                    if (step < 7) step += 1 else onSubmit(form.toRequest(step))
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FormGreen),
                        ) {
                            Text(if (step < 7) "Continue" else "Submit")
                        }
                    }
                    OutlinedButton(onClick = { onSaveDraft(form.toRequest(step)) }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text(if (busy) "Saving..." else "Save draft")
                    }
                }
                "approved" -> Text("Your seller account is approved. Order actions below are protected by backend ownership checks.", color = FormMuted, lineHeight = 20.sp)
                "suspended" -> Text("Seller actions are restricted while your store is suspended.", color = FormMuted, lineHeight = 20.sp)
                else -> Text("Your application is under review. Store management unlocks only after approval.", color = FormMuted, lineHeight = 20.sp)
            }
        }
    }
}

private fun sellerApplicationStepIssue(step: Int, form: SellerApplicationFormState): String? = when (step) {
    1 -> when {
        form.proposedStoreName.isBlank() -> "Enter the proposed store name."
        form.legalName.isBlank() -> "Enter the legal business name."
        !validSellerBusinessType(form.businessType) -> "Choose Individual or PLC."
        !validSellerTin(form.tax) -> "TIN number must contain exactly 10 digits."
        form.categories.isBlank() -> "Add at least one product category."
        form.description.isBlank() -> "Add a store description."
        else -> null
    }
    2 -> when {
        form.ownerName.isBlank() -> "Enter the owner's full name."
        form.contactName.isBlank() -> "Enter the contact person's name."
        form.phone.isBlank() -> "Enter a phone number."
        !form.email.contains("@") -> "Enter a valid email address."
        else -> null
    }
    3 -> when {
        form.region != SELLER_REGION_ADDIS_ABABA -> "Region must be Addis Ababa City."
        !validSellerSubcity(form.district) -> "Choose one Addis Ababa subcity."
        form.address.isBlank() -> "Enter the business address."
        else -> null
    }
    4 -> when {
        form.description.isBlank() -> "Add a public store description."
        form.serviceArea.isBlank() -> "Enter the service area."
        form.fulfillment.isBlank() -> "Enter the fulfillment method."
        form.handling.isBlank() -> "Enter the estimated handling time."
        else -> null
    }
    5 -> when {
        form.returnPolicy.isBlank() -> "Add the return policy."
        form.exchangePolicy.isBlank() -> "Add the exchange policy."
        form.shippingPolicy.isBlank() -> "Add the shipping policy."
        else -> null
    }
    7 -> if (form.terms && form.privacy && form.sellerPolicy && form.declaration) null
        else "Accept the terms, privacy notice, seller policy, and declaration."
    else -> null
}

@Composable
private fun SellerBusinessStep(
    proposedStoreName: String,
    onProposedStoreName: (String) -> Unit,
    legalName: String,
    onLegalName: (String) -> Unit,
    businessType: String,
    onBusinessType: (String) -> Unit,
    registration: String,
    onRegistration: (String) -> Unit,
    tax: String,
    onTax: (String) -> Unit,
    categories: String,
    onCategories: (String) -> Unit,
    description: String,
    onDescription: (String) -> Unit,
    tagline: String,
    onTagline: (String) -> Unit,
) {
    SellerField(proposedStoreName, onProposedStoreName, "Proposed store name")
    SellerField(legalName, onLegalName, "Legal business name")
    SellerChoiceRow(
        label = "Business type",
        options = SELLER_BUSINESS_TYPES,
        selected = businessType,
        onSelect = onBusinessType,
    )
    SellerField(registration, onRegistration, "Registration number")
    SellerField(tax, { value -> onTax(value.filter(Char::isDigit).take(10)) }, "TIN number")
    Text("${tax.length}/10 digits", color = if (tax.length == 10) FormGreen else FormMuted, fontSize = 12.sp)
    SellerField(categories, onCategories, "Product categories")
    SellerField(description, onDescription, "Store description", 3)
    SellerField(tagline, onTagline, "Short tagline")
}

@Composable
private fun SellerOwnerStep(ownerName: String, onOwnerName: (String) -> Unit, contactName: String, onContactName: (String) -> Unit, phone: String, onPhone: (String) -> Unit, email: String, onEmail: (String) -> Unit, supportPhone: String, onSupportPhone: (String) -> Unit, supportEmail: String, onSupportEmail: (String) -> Unit) {
    SellerField(ownerName, onOwnerName, "Owner full name")
    SellerField(contactName, onContactName, "Contact person")
    SellerField(phone, onPhone, "Phone number")
    SellerField(email, onEmail, "Email")
    SellerField(supportPhone, onSupportPhone, "Support phone")
    SellerField(supportEmail, onSupportEmail, "Support email")
}

@Composable
private fun SellerAddressStep(district: String, onDistrict: (String) -> Unit, address: String, onAddress: (String) -> Unit, locationNotes: String, onLocationNotes: (String) -> Unit) {
    SellerReadOnlyField("Country", "Ethiopia")
    SellerReadOnlyField("Region / state", SELLER_REGION_ADDIS_ABABA)
    SellerReadOnlyField("City", "Addis Ababa")
    SellerChoiceRow(
        label = "District / subcity",
        options = SELLER_ADDIS_ABABA_SUBCITIES.map { it to it },
        selected = district,
        onSelect = onDistrict,
    )
    SellerField(address, onAddress, "Address line")
    SellerField(locationNotes, onLocationNotes, "Location notes", 2)
}

@Composable
private fun SellerChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = FormInk, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options, key = { it.first }) { (value, displayLabel) ->
                val isSelected = selected == value
                Surface(
                    onClick = { onSelect(value) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) FormGreen else FormSoft,
                    contentColor = if (isSelected) FormOnColor else FormInk,
                    border = BorderStroke(1.dp, if (isSelected) FormGreen else FormBorder),
                ) {
                    Text(
                        displayLabel,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SellerReadOnlyField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = FormMuted, fontSize = 12.sp)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = FormSoft,
            border = BorderStroke(1.dp, FormBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(value, color = FormInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp))
        }
    }
}

@Composable
private fun SellerStoreStep(description: String, onDescription: (String) -> Unit, serviceArea: String, onServiceArea: (String) -> Unit, fulfillment: String, onFulfillment: (String) -> Unit, handling: String, onHandling: (String) -> Unit) {
    SellerField(description, onDescription, "Public description", 3)
    SellerField(serviceArea, onServiceArea, "Service area")
    SellerField(fulfillment, onFulfillment, "Fulfillment method")
    SellerField(handling, onHandling, "Estimated handling time")
    Text("Logo and banner uploads use the secure seller document route. Document upload cards below show current secure document status.", color = FormMuted, lineHeight = 18.sp)
}

@Composable
private fun SellerPoliciesStep(returnPolicy: String, onReturnPolicy: (String) -> Unit, exchangePolicy: String, onExchangePolicy: (String) -> Unit, shippingPolicy: String, onShippingPolicy: (String) -> Unit, warrantyPolicy: String, onWarrantyPolicy: (String) -> Unit, supportPolicy: String, onSupportPolicy: (String) -> Unit) {
    SellerField(returnPolicy, onReturnPolicy, "Return policy", 3)
    SellerField(exchangePolicy, onExchangePolicy, "Exchange policy", 3)
    SellerField(shippingPolicy, onShippingPolicy, "Shipping policy", 3)
    SellerField(warrantyPolicy, onWarrantyPolicy, "Warranty policy", 2)
    SellerField(supportPolicy, onSupportPolicy, "Customer-service policy", 2)
}

@Composable
private fun SellerReviewStep(proposedStoreName: String, ownerName: String, phone: String, email: String, city: String, returnPolicy: String, exchangePolicy: String, shippingPolicy: String, terms: Boolean, onTerms: (Boolean) -> Unit, privacy: Boolean, onPrivacy: (Boolean) -> Unit, sellerPolicy: Boolean, onSellerPolicy: (Boolean) -> Unit, declaration: Boolean, onDeclaration: (Boolean) -> Unit) {
    SellerSummaryRow("Store", proposedStoreName)
    SellerSummaryRow("Owner", ownerName)
    SellerSummaryRow("Contact", "$phone $email")
    SellerSummaryRow("City", city)
    SellerSummaryRow("Policies", listOf(returnPolicy, exchangePolicy, shippingPolicy).count { it.isNotBlank() }.toString() + "/3 complete")
    SellerCheckbox("I accept the seller terms", terms, onTerms)
    SellerCheckbox("I acknowledge the privacy notice", privacy, onPrivacy)
    SellerCheckbox("I accept the seller policy", sellerPolicy, onSellerPolicy)
    SellerCheckbox("I declare this information is accurate", declaration, onDeclaration)
}

@Composable
private fun SellerApplicationProgress(step: Int, onStep: (Int) -> Unit) {
    val labels: List<String> = listOf("Business", "Owner", "Address", "Store", "Policies", "Docs", "Review")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels.size) { index ->
            val number = index + 1
            Surface(
                color = if (step == number) FormGreen else FormSoft,
                contentColor = if (step == number) FormOnColor else FormInk,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.clickable { onStep(number) },
            ) {
                Text("${number}. ${labels[index]}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun SellerField(value: String, onValue: (String) -> Unit, label: String, minLines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        singleLine = minLines == 1,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = FormInk,
            unfocusedTextColor = FormInk,
            focusedContainerColor = FormSurface,
            unfocusedContainerColor = FormSurface,
            cursorColor = FormGreen,
            focusedBorderColor = FormGreen,
            unfocusedBorderColor = FormBorder,
            focusedLabelColor = FormGreen,
            unfocusedLabelColor = FormMuted,
        ),
    )
}

@Composable
private fun SellerCheckbox(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChecked(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, color = FormInk, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SellerSummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = FormMuted, fontSize = 12.sp)
        Text(value.ifBlank { "Not set" }, color = FormInk, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SellerDocumentStatusList(
    applicationExists: Boolean,
    documents: List<ShopSellerApplicationDocumentDto>,
    busy: Boolean,
    onUploadDocument: (String, ByteArray, String, String) -> Unit,
    onRemoveDocument: (String) -> Unit,
) {
    val context = LocalContext.current
    var pendingDocumentType by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val documentType = pendingDocumentType
        pendingDocumentType = null
        if (uri != null && documentType != null) {
            runCatching {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri).orEmpty().lowercase()
                require(mimeType in setOf("image/jpeg", "image/png", "application/pdf")) {
                    "Upload JPG, PNG or PDF only."
                }
                val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }.orEmpty().ifBlank { "document" }
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Document could not be read.")
                require(bytes.isNotEmpty()) { "Choose a document to upload." }
                require(bytes.size <= 10 * 1024 * 1024) { "Document must be 10 MB or smaller." }
                onUploadDocument(documentType, bytes, fileName, mimeType)
            }.onSuccess {
                localError = null
            }.onFailure {
                localError = it.message ?: "Document could not be prepared."
            }
        }
    }
    val documentTypes = listOf(
        "store_logo" to "Store logo",
        "store_banner" to "Store banner",
        "government_id" to "Government ID",
        "business_license" to "Business license",
        "tax_registration" to "Tax registration",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Documents", color = FormInk, fontWeight = FontWeight.Black)
        Text("Upload government ID, business license or tax registration when required. Documents remain private and are reviewed by Admin only.", color = FormMuted, lineHeight = 18.sp)
        if (!applicationExists) {
            SellerEmptyCard("Enter a store name and save your draft before uploading documents.")
        }
        localError?.let { Text(it, color = FormRed, fontWeight = FontWeight.Medium) }
        documentTypes.forEach { (documentType, label) ->
            val document = documents.firstOrNull { it.documentType == documentType && it.status != "removed" }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FormSoft).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = FormInk, fontWeight = FontWeight.SemiBold)
                    Text(
                        document?.let { "${formBackendLabel(it.status)} - ${it.byteSize / 1024} KB" } ?: "Not uploaded",
                        color = FormMuted,
                        fontSize = 12.sp,
                    )
                }
                IconButton(
                    onClick = {
                        pendingDocumentType = documentType
                        picker.launch(arrayOf("image/jpeg", "image/png", "application/pdf"))
                    },
                    enabled = applicationExists && !busy,
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = if (document == null) "Upload $label" else "Replace $label", tint = FormGreen)
                }
                if (document != null) {
                    IconButton(onClick = { onRemoveDocument(document.id) }, enabled = !busy) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove $label", tint = FormRed)
                    }
                } else {
                    Icon(Icons.Default.Security, contentDescription = "Private document", tint = FormGreen)
                }
            }
        }
    }
}

@Composable
private fun SellerEmptyCard(message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(FormSoft), border = BorderStroke(1.dp, FormLine)) {
        Text(message, color = FormMuted, modifier = Modifier.padding(14.dp), lineHeight = 18.sp)
    }
}

internal fun sellerStatusColor(status: String): Color = when (status) {
    "approved" -> FormGreen
    "rejected", "suspended" -> FormRed
    "submitted", "under_review", "pending" -> FormOrangeDark
    else -> FormMuted
}

private fun formBackendLabel(value: String?): String = value
    .orEmpty()
    .replace("_", " ")
    .split(" ")
    .filter { it.isNotBlank() }
    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
    .ifBlank { "Unknown" }
