package com.tutorspoint.tutor;

import com.tutorspoint.common.api.ApiResponse;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.storage.MultipartUploads;
import com.tutorspoint.tutor.dto.TutorProfileDto;
import com.tutorspoint.tutor.dto.TutorProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/**
 * Tutor profiles: the owner-scoped routes under {@code /me}, and one public read (FR-T1 -
 * FR-T8, FR-S4).
 *
 * <p>The {@code /me} routes carry no tutor id, so a token cannot name anybody but its owner.
 * The public route does carry one, and it is the reason the service filters on status inside
 * the query: an id that belongs to a tutor who is still drafting answers exactly as an id that
 * belongs to nobody.
 *
 * <p>The photograph and the video have endpoints of their own rather than fields on the draft
 * save, because a file has to be inspected, re-encoded and stored before the profile may point
 * at it - and because that makes an upload a single request rather than an upload followed by
 * a save that might not happen.
 *
 * <p>As in {@code ReferenceController}, this is the only layer that knows an
 * {@code Accept-Language} header exists; below it there is only a {@link Language}.
 */
@RestController
@RequestMapping("/api/tutors")
@RequiredArgsConstructor
@Tag(name = "Tutor profiles", description = "Drafting, publishing and viewing a tutor profile")
public class TutorProfileController {

    private final TutorProfileService tutorProfileService;

    @GetMapping("/me/profile")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "My profile",
            description = "Whatever state it is in. A tutor who has never opened the wizard gets "
                    + "an empty draft, with every requirement listed in missingFields.")
    public ApiResponse<TutorProfileDto> myProfile(Locale locale) {
        return ApiResponse.ok(tutorProfileService.myProfile(Language.fromLocale(locale)));
    }

    @PutMapping("/me/profile")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Save my profile draft",
            description = "Saves the whole profile and never changes whether it is published. "
                    + "Reference values are sent as codes, the same codes /api/reference serves.")
    public ApiResponse<TutorProfileDto> updateMyProfile(@Valid @RequestBody TutorProfileRequest request,
                                                        Locale locale) {
        return ApiResponse.ok(tutorProfileService.updateMyProfile(request, Language.fromLocale(locale)));
    }

    @PostMapping("/me/profile/publish")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Publish my profile",
            description = "Refused with PROFILE_INCOMPLETE while anything in missingFields is outstanding.")
    public ApiResponse<TutorProfileDto> publishMyProfile(Locale locale) {
        return ApiResponse.ok(tutorProfileService.publishMyProfile(Language.fromLocale(locale)));
    }

    @PostMapping("/me/profile/unpublish")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Unpublish my profile",
            description = "Takes the profile off the platform. The content is kept, so publishing "
                    + "again is one call.")
    public ApiResponse<TutorProfileDto> unpublishMyProfile(Locale locale) {
        return ApiResponse.ok(tutorProfileService.unpublishMyProfile(Language.fromLocale(locale)));
    }

    @PostMapping(path = "/me/profile/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Upload my profile photo",
            description = "JPEG or PNG, up to 5 MB. The image is re-encoded before it is stored, "
                    + "which strips the Exif metadata - including the GPS position a phone "
                    + "camera records - and caps its longest edge.")
    public ApiResponse<TutorProfileDto> uploadPhoto(@RequestParam("file") MultipartFile file, Locale locale) {
        return ApiResponse.ok(tutorProfileService.uploadPhoto(MultipartUploads.read(file), Language.fromLocale(locale)));
    }

    @DeleteMapping("/me/profile/photo")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Remove my profile photo",
            description = "The profile becomes incomplete, and cannot be published until another "
                    + "photo is uploaded.")
    public ApiResponse<TutorProfileDto> removePhoto(Locale locale) {
        return ApiResponse.ok(tutorProfileService.removePhoto(Language.fromLocale(locale)));
    }

    @PostMapping(path = "/me/profile/intro-video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Upload my intro video", description = "MP4, up to 5 MB. Optional.")
    public ApiResponse<TutorProfileDto> uploadIntroVideo(@RequestParam("file") MultipartFile file, Locale locale) {
        return ApiResponse.ok(tutorProfileService.uploadIntroVideo(MultipartUploads.read(file), Language.fromLocale(locale)));
    }

    @DeleteMapping("/me/profile/intro-video")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Remove my intro video")
    public ApiResponse<TutorProfileDto> removeIntroVideo(Locale locale) {
        return ApiResponse.ok(tutorProfileService.removeIntroVideo(Language.fromLocale(locale)));
    }

    @GetMapping("/{tutorId}")
    @Operation(summary = "A tutor profile",
            description = "Public, and published profiles only. Carries no email address and no "
                    + "phone number: first contact goes through the platform.")
    public ApiResponse<TutorProfileDto> publicProfile(@PathVariable Long tutorId, Locale locale) {
        return ApiResponse.ok(tutorProfileService.publicProfile(tutorId, Language.fromLocale(locale)));
    }
}
