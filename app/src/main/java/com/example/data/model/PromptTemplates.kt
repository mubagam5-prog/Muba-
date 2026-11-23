package com.example.data.model

enum class PromptCategory(val label: String) {
    ALL("All"),
    CINEMATIC("Cinematic"),
    PORTRAIT("Portrait"),
    SCIFI("Sci-Fi"),
    NATURE("Nature"),
    STUDIO("Studio")
}

data class PromptTemplate(
    val id: String,
    val title: String,
    val category: PromptCategory,
    val promptText: String,
    val tip: String,
    val badge: String
)

object PromptIdeaCatalog {

    val imageTemplates: List<PromptTemplate> = listOf(
        PromptTemplate(
            id = "img_neon_supercar",
            title = "Neon Rain Supercar",
            category = PromptCategory.CINEMATIC,
            promptText = "Futuristic obsidian sports car parked on wet asphalt under purple neon rain, dramatic reflections, shallow depth of field, 35mm cinematic photography, ultra detailed",
            tip = "Swap 'sports car' for a motorcycle or vintage coupe for instant variation.",
            badge = "16:9 Best"
        ),
        PromptTemplate(
            id = "img_violet_portrait",
            title = "Violet Rim Studio Portrait",
            category = PromptCategory.PORTRAIT,
            promptText = "Minimalist editorial studio portrait with soft violet rim lighting, dark obsidian backdrop, sharp catchlights in eyes, medium format 85mm lens, crisp skin texture",
            tip = "Add wardrobe details or mood expressions to personalize the portrait.",
            badge = "85mm Studio"
        ),
        PromptTemplate(
            id = "img_crystal_ocean",
            title = "Crystal Ocean Sanctuary",
            category = PromptCategory.NATURE,
            promptText = "Floating crystal islands hovering above a calm midnight bioluminescent ocean, volumetric starlight beams, serene atmosphere, ultra detailed 3D concept art",
            tip = "Replace 'midnight' with 'golden sunrise' for warm atmospheric lighting.",
            badge = "3D Art"
        ),
        PromptTemplate(
            id = "img_tokyo_holograms",
            title = "Tokyo Dusk Holograms",
            category = PromptCategory.SCIFI,
            promptText = "Cyberpunk Tokyo rooftop overlooking glowing holographic billboards at dusk, gentle rain mist, moody violet and cyan palette, wide-angle cinematic composition",
            tip = "Pair with 16:9 widescreen framing to capture the full city skyline.",
            badge = "Cyberpunk"
        ),
        PromptTemplate(
            id = "img_obsidian_watch",
            title = "Obsidian Luxury Watch",
            category = PromptCategory.STUDIO,
            promptText = "Macro product shot of a matte black mechanical watch resting on dark volcanic stone, soft overhead studio softbox, subtle purple edge highlights, hyper-realistic",
            tip = "Works well for sneakers, headphones, perfume bottles, or tech gadgets.",
            badge = "Macro"
        ),
        PromptTemplate(
            id = "img_alpine_aurora",
            title = "Alpine Aurora Cabin",
            category = PromptCategory.NATURE,
            promptText = "Cozy glass A-frame cabin nestled in snow-covered pine mountains beneath vivid emerald and violet aurora borealis, warm interior lantern glow, long-exposure photography",
            tip = "Try 9:16 vertical aspect ratio to create a crisp mobile wallpaper.",
            badge = "9:16 Wallpaper"
        ),
        PromptTemplate(
            id = "img_golden_botanist",
            title = "Golden Hour Botanist",
            category = PromptCategory.PORTRAIT,
            promptText = "Sun-drenched editorial portrait framed by lush tropical monstera leaves, warm golden hour sunlight filtering through foliage, natural bokeh, soft analog film grain",
            tip = "Specify 'overhead canopy shadows' for stronger dramatic sunlight patterns.",
            badge = "Film Look"
        ),
        PromptTemplate(
            id = "img_orbital_dome",
            title = "Orbital Conservatory",
            category = PromptCategory.SCIFI,
            promptText = "Sleek futuristic glass botanical dome inside a space station orbiting a ringed violet planet, floating dust motes, symmetrical architectural perspective, 8K render",
            tip = "Strong symmetry makes this template stand out in 1:1 square framing.",
            badge = "1:1 Symmetry"
        ),
        PromptTemplate(
            id = "img_rainy_jazz",
            title = "Rainy Jazz Alley",
            category = PromptCategory.CINEMATIC,
            promptText = "Atmospheric cobblestone street after midnight rain, warm amber streetlamps reflecting in puddles, silhouette of a saxophonist in distant mist, film noir aesthetic",
            tip = "Swap 'amber' with 'electric violet' for a modern neon noir tone.",
            badge = "Noir"
        ),
        PromptTemplate(
            id = "img_ceramic_trio",
            title = "Sculptural Ceramic Trio",
            category = PromptCategory.STUDIO,
            promptText = "Minimalist matte terracotta and obsidian ceramic vases on a travertine plinth, soft morning window shadows, architectural editorial photography, crisp natural light",
            tip = "Change materials to 'frosted glass' or 'brushed titanium' for fresh textures.",
            badge = "Minimal"
        )
    )

    val videoTemplates: List<PromptTemplate> = listOf(
        PromptTemplate(
            id = "vid_golden_breeze",
            title = "Golden Breeze & Particles",
            category = PromptCategory.CINEMATIC,
            promptText = "Gentle golden sunset breeze moving across the scene with warm glowing light particles drifting slowly through crisp evening air",
            tip = "Ideal for outdoor landscapes, portraits, and warm architectural shots.",
            badge = "Slow Pan"
        ),
        PromptTemplate(
            id = "vid_neon_mist",
            title = "Violet Neon Mist Zoom",
            category = PromptCategory.SCIFI,
            promptText = "Slow dramatic cinematic zoom forward through swirling purple neon mist with subtle reflective shimmer and deep shadows",
            tip = "Matches dark cityscapes, cars, and neon-lit portraits.",
            badge = "Slow Zoom"
        ),
        PromptTemplate(
            id = "vid_parallax_drift",
            title = "Volumetric Parallax Drift",
            category = PromptCategory.NATURE,
            promptText = "Dreamy layered parallax camera drift as soft volumetric sunbeams and floating dust motes glide calmly across the frame",
            tip = "Creates depth on scenic photos with distinct foreground and sky.",
            badge = "Parallax"
        ),
        PromptTemplate(
            id = "vid_twilight_embers",
            title = "Twilight Floating Embers",
            category = PromptCategory.CINEMATIC,
            promptText = "Dramatic twilight atmosphere with glowing amber embers rising gracefully and subtle cinematic haze across the horizon",
            tip = "Pair with 10s duration and Obsidian Echo music for epic pacing.",
            badge = "Embers"
        ),
        PromptTemplate(
            id = "vid_editorial_reveal",
            title = "Editorial Soft Push-In",
            category = PromptCategory.PORTRAIT,
            promptText = "Subtle intimate push-in toward the subject with silky studio rim light shimmer and gentle depth-of-field sparkle",
            tip = "Keeps facial details sharp while adding studio-grade motion.",
            badge = "Push-In"
        ),
        PromptTemplate(
            id = "vid_ocean_shimmer",
            title = "Oceanic Shimmer Sway",
            category = PromptCategory.NATURE,
            promptText = "Calm rhythmic camera sway paired with tranquil twilight water reflections and cool bioluminescent highlights",
            tip = "Best paired with Midnight Velvet audio in the post-generation editor.",
            badge = "Gentle Sway"
        ),
        PromptTemplate(
            id = "vid_cyber_surge",
            title = "Holographic Pulse Surge",
            category = PromptCategory.SCIFI,
            promptText = "Dynamic forward glide energized by futuristic violet light streaks, crisp cyber contrast, and rhythmic ambient energy",
            tip = "Try Cyber Crisp filter + Neon Pulse music after generating.",
            badge = "Cyber Glide"
        ),
        PromptTemplate(
            id = "vid_luxury_turntable",
            title = "Luxury Studio Reveal",
            category = PromptCategory.STUDIO,
            promptText = "Smooth studio hero reveal with sleek metallic specular highlights, crisp edge lighting, and soft obsidian vignette framing",
            tip = "Great for product photos, watches, sneakers, and minimalist setups.",
            badge = "Hero Reveal"
        ),
        PromptTemplate(
            id = "vid_misty_sunrise",
            title = "Misty Forest Sunrise",
            category = PromptCategory.NATURE,
            promptText = "Peaceful morning fog drifting softly between silhouettes as warm first-light rays awaken the horizon",
            tip = "Works nicely with Golden Glow filter for soft morning warmth.",
            badge = "Fog Drift"
        ),
        PromptTemplate(
            id = "vid_noir_rain",
            title = "Noir Midnight Rain",
            category = PromptCategory.CINEMATIC,
            promptText = "Moody high-contrast camera crawl with falling silver rain streaks and gleaming wet surface reflections",
            tip = "Combine with Obsidian Noir filter in the editor for a monochrome look.",
            badge = "Noir Rain"
        )
    )
}
