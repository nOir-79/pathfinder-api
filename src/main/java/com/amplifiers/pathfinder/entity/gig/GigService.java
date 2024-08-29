package com.amplifiers.pathfinder.entity.gig;

import com.amplifiers.pathfinder.entity.enrollment.EnrollmentRepository;
import com.amplifiers.pathfinder.entity.gig.GigRepository.Specs;
import com.amplifiers.pathfinder.entity.image.Image;
import com.amplifiers.pathfinder.entity.image.ImageService;
import com.amplifiers.pathfinder.entity.review.ReviewRepository;
import com.amplifiers.pathfinder.entity.tag.Tag;
import com.amplifiers.pathfinder.entity.tag.TagCreateRequest;
import com.amplifiers.pathfinder.entity.tag.TagService;
import com.amplifiers.pathfinder.entity.user.User;
import com.amplifiers.pathfinder.entity.user.UserShortDTO;
import com.amplifiers.pathfinder.entity.video.Video;
import com.amplifiers.pathfinder.entity.video.VideoService;
import com.amplifiers.pathfinder.exception.ResourceNotFoundException;
import com.amplifiers.pathfinder.exception.ValidationException;
import com.amplifiers.pathfinder.recommendation.RecommendationService;
import com.amplifiers.pathfinder.utility.Category;
import com.amplifiers.pathfinder.utility.UserUtility;
import com.amplifiers.pathfinder.utility.Variables.PaginationSettings;
import com.recombee.api_client.bindings.RecommendationResponse;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class GigService {

    private final UserUtility userUtility;

    private final GigRepository repository;
    private final ReviewRepository reviewRepository;
    private final EnrollmentRepository enrollmentRepository;

    private final TagService tagService;
    private final ImageService imageService;
    private final VideoService videoService;

    private final JdbcTemplate jdbcTemplate;
    private final RecommendationService recommendationService;

    public void gigCreateRequestValidation(GigCreateRequest request) {
        if (request.getTags().size() < 3) {
            throw new ValidationException("At least three tags required.");
        }

        if (request.getFaqs().size() < 1) {
            throw new ValidationException("At least one faqs required.");
        }
    }

    public Gig createGig(GigCreateRequest request) {
        User user = userUtility.getCurrentUser();

        gigCreateRequestValidation(request);

        request.getTags().forEach(name -> tagService.findByName(name).orElseGet(() -> tagService.createTag(new TagCreateRequest(name))));

        Set<Tag> tags = request.getTags().stream().map(name -> tagService.findByName(name).get()).collect(Collectors.toSet());

        var gig = Gig.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .price(request.getPrice())
            .offerText(request.getOfferText())
            .category(request.getCategory())
            .rating(0.0f)
            .accepted(false)
            .seller(user)
            .tags(tags)
            .faqs(request.getFaqs())
            .createdAt(OffsetDateTime.now())
            .build();

        Gig savedGig = repository.save(gig);
        recommendationService.sendGigValues(savedGig);
        return savedGig;
    }

    private Boolean isGigOfUser(Gig gig) {
        User user = userUtility.getCurrentUser();

        return Objects.equals(user.getId(), gig.getSeller().getId());
    }

    public Page<Gig> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public GigPageDTO findById(Integer id) {
        Gig gig = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Gig not found"));

        Video video = gig.getGigVideo();

        if (video != null) {
            if (
                video.getPresignedUrl() == null ||
                video.getPresignedUrlExpire() == null ||
                OffsetDateTime.now().isAfter(video.getPresignedUrlExpire())
            ) {
                videoService.createVideoPresignedUrl(video);
            }
        }

        return createGigPageDTO(gig);
    }

    public GigPageDTO privateFindById(Integer id) {
        Gig gig = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Gig not found"));

        Video video = gig.getGigVideo();

        if (video != null) {
            if (
                video.getPresignedUrl() == null ||
                video.getPresignedUrlExpire() == null ||
                OffsetDateTime.now().isAfter(video.getPresignedUrlExpire())
            ) {
                videoService.createVideoPresignedUrl(video);
            }
        }

        User currentUser = userUtility.getCurrentUser();
        if (!Objects.equals(currentUser.getId(), gig.getId())) {
            recommendationService.addDetailView(currentUser.getId(), gig.getId());
            System.out.println("added detail view");
        }

        return createGigPageDTO(gig);
    }

    public String deleteGig(Integer gigId) {
        Gig gig = repository.findById(gigId).orElseThrow(() -> new ResourceNotFoundException("Gig not found"));

        User user = userUtility.getCurrentUser();

        if (!user.getId().equals(gig.getSeller().getId())) {
            throw new ValidationException("Only the owner of the gig can delete it.");
        }

        imageService.deleteImageById(gig.getGigCoverImage().getId());
        videoService.deleteVideo(gig.getGigVideo().getId());

        // INFO: because cascading delete wasn't working for many to many for some reason.
        jdbcTemplate.update("DELETE FROM gig_tag WHERE gigId = ?", gigId);
        repository.deleteById(gigId);

        return "deleted";
    }

    public void editGig(GigCreateRequest request, Integer gigId) {
        Gig gig = repository.findById(gigId).orElseThrow(() -> new ResourceNotFoundException("Gig not found"));

        if (!isGigOfUser(gig)) {
            throw new ValidationException("User not owner of this gig");
        }

        gigCreateRequestValidation(request);

        request.getTags().forEach(name -> tagService.findByName(name).orElseGet(() -> tagService.createTag(new TagCreateRequest(name))));

        Set<Tag> tags = request.getTags().stream().map(name -> tagService.findByName(name).get()).collect(Collectors.toSet());

        gig.setTitle(request.getTitle());
        gig.setDescription(request.getDescription());
        gig.setPrice(request.getPrice());
        gig.setOfferText(request.getOfferText());
        gig.setCategory(request.getCategory());
        gig.setTags(tags);
        gig.setFaqs(request.getFaqs());

        repository.save(gig);
    }

    public Image setCoverImage(Integer gigId, MultipartFile image) throws Exception {
        Gig gig = repository.findById(gigId).orElseThrow(() -> new ResourceNotFoundException("Gig id " + gigId + " does not exist."));

        if (!isGigOfUser(gig)) {
            throw new ValidationException("User not owner of this gig");
        }

        Image prevCoverImage = gig.getGigCoverImage();

        Image coverImage = imageService.saveImage(image);

        gig.setGigCoverImage(coverImage);
        repository.save(gig);

        if (prevCoverImage != null) {
            imageService.deleteImageById(prevCoverImage.getId());
        }

        return coverImage;
    }

    public Video setGigVideo(Integer gigId, MultipartFile video) throws Exception {
        Gig gig = repository.findById(gigId).orElseThrow(() -> new ResourceNotFoundException("Gig id " + gigId + " does not exist."));

        if (!isGigOfUser(gig)) {
            throw new ValidationException("User not owner of this gig");
        }

        Video prevGigVideo = gig.getGigVideo();

        Video gigVideo = videoService.saveVideo(video);

        gig.setGigVideo(gigVideo);
        repository.save(gig);

        if (prevGigVideo != null) {
            videoService.deleteVideo(prevGigVideo.getId());
        }

        return gigVideo;
    }

    public List<Gig> getGigsBySeller(User seller) {
        return repository.findGigsBySeller(seller);
    }

    public GigPageDTO createGigPageDTO(Gig gig) {
        List<Float> ratingList = reviewRepository.findAllByGigId(gig.getId()).stream().map(r -> Float.valueOf(r.getRating())).toList();

        float rating = ratingList.size() > 0 ? (ratingList.stream().reduce((float) 0, Float::sum)) / ratingList.size() : 0;

        return GigPageDTO.builder()
            .id(gig.getId())
            .title(gig.getTitle())
            .category(gig.getCategory())
            .description(gig.getDescription())
            .offerText(gig.getOfferText())
            .price(gig.getPrice())
            .gigCoverImage(gig.getGigCoverImage() != null ? gig.getGigCoverImage().getFilename() : null)
            .gigVideo(gig.getGigVideo() != null ? gig.getGigVideo().getPresignedUrl() : null)
            .tags(gig.getTags().stream().map(Tag::getName).toList())
            .faqs(gig.getFaqs())
            .rating(rating)
            .totalReviews(ratingList.size())
            .totalCompleted(enrollmentRepository.countByGigAndCompletedAtNotNull(gig))
            .totalOrders(enrollmentRepository.countByGig(gig))
            .accepted(gig.isAccepted())
            .seller(
                UserShortDTO.builder()
                    .id(gig.getSeller().getId())
                    .firstName(gig.getSeller().getFirstName())
                    .lastName(gig.getSeller().getLastName())
                    .build()
            )
            .createdAt(gig.getCreatedAt())
            .build();
    }

    public static GigShortDTO createGigShortDTO(Gig gig) {
        return GigShortDTO.builder()
            .id(gig.getId())
            .title(gig.getTitle())
            .coverImage(gig.getGigCoverImage() != null ? gig.getGigCoverImage().getFilename() : null)
            .build();
    }

    public GigCardDTO createGigCardDTO(Gig gig, boolean includeUser) {
        List<Float> ratingList = reviewRepository.findAllByGigId(gig.getId()).stream().map(r -> Float.valueOf(r.getRating())).toList();

        float rating = ratingList.size() > 0 ? (ratingList.stream().reduce((float) 0, Float::sum)) / ratingList.size() : 0;

        int ratedByCount = ratingList.size();

        var gigCardDTOBuilder = GigCardDTO.builder()
            .id(gig.getId())
            .title(gig.getTitle())
            .price(gig.getPrice())
            .rating(rating)
            .ratedByCount(ratedByCount)
            .coverImage(gig.getGigCoverImage() != null ? gig.getGigCoverImage().getFilename() : null)
            .tags(gig.getTags().stream().map(Tag::getName).collect(Collectors.toSet()));

        if (includeUser) {
            UserShortDTO userShortDTO = UserShortDTO.builder()
                .id(gig.getSeller().getId())
                .firstName(gig.getSeller().getFirstName())
                .lastName(gig.getSeller().getLastName())
                .build();

            gigCardDTOBuilder = gigCardDTOBuilder.user(userShortDTO);
        }

        return gigCardDTOBuilder.build();
    }

    public GigCardDTO createGigCardDTO(Gig gig) {
        return createGigCardDTO(gig, true);
    }

    public List<GigCardDTO> getGigCardsBySeller(User seller) {
        List<Gig> gigs = repository.findGigsBySeller(seller);

        return gigs.stream().map(g -> createGigCardDTO(g, false)).toList();
    }

    // Recombee
    // getting recommended gigs for a user

    public Object getRecommendationsForUser(String scenario) {
        User user = userUtility.getCurrentUser();
        RecommendationResponse recommended = recommendationService.getRecommendationsForUser(
            user.getId(),
            PaginationSettings.NUM_RECOMMENDED_GIGS,
            scenario
        );

        return getReturnDataForRecommendation(recommended);
    }

    public Object getRecommendationsForAnonymousUsers(String scenario) {
        // for globally popular gigs.
        // -1 means no user. handled in recommendation service.
        RecommendationResponse recommended = recommendationService.getRecommendationsForUser(
            -1,
            PaginationSettings.NUM_RECOMMENDED_GIGS,
            scenario
        );

        return getReturnDataForRecommendation(recommended);
    }

    public Object getRecommendationsForItem(Integer gigId, String scenario) {
        User user = userUtility.getCurrentUser();
        RecommendationResponse recommended = recommendationService.getRecommendationsForItem(
            gigId,
            user.getId(),
            PaginationSettings.NUM_RECOMMENDED_GIGS,
            scenario
        );

        return getReturnDataForRecommendation(recommended);
    }

    public Object getNextRecommendationsForUser(String recommId) {
        RecommendationResponse recommended = recommendationService.getNextRecommendationsForUser(
            recommId,
            PaginationSettings.NUM_RECOMMENDED_GIGS
        );
        return getReturnDataForRecommendation(recommended);
    }

    public Object getReturnDataForRecommendation(RecommendationResponse recommended) {
        String recommId = recommended.getRecommId();
        String[] recomms = recommended.getIds();

        Integer[] recommsInt = new Integer[recomms.length];
        for (int i = 0; i < recomms.length; i++) {
            recommsInt[i] = Integer.parseInt(recomms[i]);
        }

        // Fetch gigs and create DTOs
        List<GigCardDTO> recommendedGigs = repository.findAllById(List.of(recommsInt)).stream().map(this::createGigCardDTO).toList();

        // Using a Map to hold return data
        Map<String, Object> returnData = new HashMap<>();
        returnData.put("recommId", recommId);
        returnData.put("gigs", recommendedGigs);

        return returnData;
    }

    // INFO: HOW SEARCH WORKS.
    // when a user starts typing the query, we recommend them the tags and categories with the 'query' prefix.
    // If a user clicks on any of those categories or tags, we show em the gigs for that category or tag.
    // If a user doesn't click any of the tags/categories in the suggestion(under the search box), and clicks
    // enter after typing the search term, we show them the relevant gigs for the search term. i.e. the gigs where
    // the title/description/category/tag contain the search term.

    // we will get the results based on scores of the gigs with the touch of a bit of randomness so people can
    // explore unpopular gigs often. i.e. 20% randomness. // TODO: improve this fetching algorithm.

    // TODO: implement a scoring algorithm.
    // TODO: update all gig fetch functions to incorporate scoring.

    //    public List<Gig> getSimilarGigs() {
    //        // TODO
    //    }

    //    public List getCategoryAndTagsForKeyword() {
    //        // TODO: return whether it's a category or tag. so we can call the corresponding function.
    //        // ie. findByCategory() or findByQuery().
    //    }

    public Page<Gig> findByCategory(Pageable pageable, String category) {
        return repository.findByCategory(pageable, category);
    }

    public Page<GigCardDTO> findByQuery(
        Pageable pageable,
        String query,
        Float ratingAbove,
        Float budget,
        Category category,
        List<String> tagStrings
    ) {
        Specification<Gig> specification = Specs.isLike(query).and(Specs.isRatingAbove(ratingAbove));

        if (budget != null) {
            specification = specification.and(Specs.isPriceUnder(budget));
        }

        if (category != null) {
            specification = specification.and(Specs.inCategory(category));
        }

        if (tagStrings != null && !tagStrings.isEmpty()) {
            List<Tag> tags = tagStrings
                .stream()
                .map(t -> tagService.findByName(t).orElseThrow(() -> new ResourceNotFoundException("Tag doesn't exist")))
                .toList();

            specification = specification.and(Specs.hasTags(tags));
        }

        var gigs = repository.findAll(specification, pageable);

        return gigs.map(g -> createGigCardDTO(g, true));
    }
    //    public List<Gig> findByTag(String tag) {
    //        // TODO
    //    }

    //    public List<Gig> getFeaturedGigs() {
    //        // TODO
    //    }
    //
    //    // For explore new talents feature.
    //    public List<Gig> getNewGigs() {
    //        // TODO
    //    }
}
