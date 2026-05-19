public class TerrainShaderSource {
    public static final String VERTEX_SHADER = 
        "#version 330 core\n" +
        "layout (location = 0) in vec3 aPos;\n" +
        "layout (location = 1) in float aType;\n" +
        "layout (location = 2) in float aBiome;\n" +
        "layout (location = 3) in float aElevation;\n" +
        "layout (location = 4) in float aVariation;\n" +
        "layout (location = 5) in float aFrame;\n" +
        "\n" +
        "out float vType;\n" +
        "out float vBiome;\n" +
        "out float vElevation;\n" +
        "out float vVariation;\n" +
        "out float vFrame;\n" +
        "out vec3 vWorldPos;\n" +
        "\n" +
        "uniform mat4 model;\n" +
        "uniform mat4 view;\n" +
        "uniform mat4 projection;\n" +
        "\n" +
        "void main() {\n" +
        "    vType = aType;\n" +
        "    vBiome = aBiome;\n" +
        "    vElevation = aElevation;\n" +
        "    vVariation = aVariation;\n" +
        "    vFrame = aFrame;\n" +
        "    vWorldPos = vec3(model * vec4(aPos, 1.0));\n" +
        "    gl_Position = projection * view * model * vec4(aPos, 1.0);\n" +
        "}";

    public static final String FRAGMENT_SHADER = 
        "#version 330 core\n" +
        "in float vType;\n" +
        "in float vBiome;\n" +
        "in float vElevation;\n" +
        "in float vVariation;\n" +
        "in float vFrame;\n" +
        "in vec3 vWorldPos;\n" +
        "\n" +
        "uniform sampler2D elevationTex;\n" +
        "\n" +
        "out vec4 FragColor;\n" +
        "\n" +
        "// Tile Types\n" +
        "const float TYPE_PLAIN = 0.0;\n" +
        "const float TYPE_WATER = 1.0;\n" +
        "const float TYPE_ROCK  = 2.0;\n" +
        "const float TYPE_SAND  = 3.0;\n" +
        "const float TYPE_SNOW  = 4.0;\n" +
        "const float TYPE_ICE   = 5.0;\n" +
        "const float TYPE_TREES = 6.0;\n" +
        "\n" +
        "// Biomes\n" +
        "const float BIOME_NONE      = 0.0;\n" +
        "const float BIOME_GRASSLAND = 1.0;\n" +
        "const float BIOME_DESERT    = 2.0;\n" +
        "const float BIOME_SAVANNAH  = 3.0;\n" +
        "const float BIOME_TUNDRA    = 4.0;\n" +
        "const float BIOME_TAIGA     = 5.0;\n" +
        "\n" +
        "void main() {\n" +
        "    vec3 color = vec3(0.5);\n" +
        "    \n" +
        "    if (vType == TYPE_WATER) {\n" +
        "        color = vec3(0.1, 0.4, 0.8);\n" +
        "    } else if (vType == TYPE_ROCK) {\n" +
        "        color = vec3(0.4, 0.4, 0.4);\n" +
        "    } else if (vType == TYPE_SAND) {\n" +
        "        color = vec3(0.9, 0.8, 0.6);\n" +
        "    } else if (vType == TYPE_SNOW) {\n" +
        "        color = vec3(0.9, 0.9, 1.0);\n" +
        "    } else if (vType == TYPE_ICE) {\n" +
        "        color = vec3(0.8, 0.9, 1.0);\n" +
        "    } else if (vType == TYPE_TREES) {\n" +
        "        color = vec3(0.1, 0.4, 0.1);\n" +
        "    } else {\n" +
        "        // Handle Biome-based Plain colors\n" +
        "        if (vBiome == BIOME_DESERT) color = vec3(0.8, 0.7, 0.5);\n" +
        "        else if (vBiome == BIOME_SAVANNAH) color = vec3(0.7, 0.7, 0.3);\n" +
        "        else if (vBiome == BIOME_TUNDRA) color = vec3(0.8, 0.8, 0.9);\n" +
        "        else if (vBiome == BIOME_TAIGA) color = vec3(0.2, 0.3, 0.1);\n" +
        "        else color = vec3(0.3, 0.6, 0.2); // Grassland\n" +
        "    }\n" +
        "    \n" +
        "    // True 3D position reconstruction from the 2D projected space\n" +
        "    vec3 truePos = vec3(vWorldPos.x, vWorldPos.y + vElevation, vElevation);\n" +
        "    \n" +
        "    // Compute flat surface normal via standard derivatives\n" +
        "    vec3 normal = normalize(cross(dFdx(truePos), dFdy(truePos)));\n" +
        "    \n" +
        "    // Sun directional lighting (matching GridManager's sunVecX=0.8, sunVecY=0.6)\n" +
        "    vec3 lightDir = normalize(vec3(-0.8, -0.6, 1.0));\n" +
        "    \n" +
        "    // Simple ambient + diffuse lighting\n" +
        "    float ambient = 0.4;\n" +
        "    float diff = max(dot(normal, lightDir), 0.0);\n" +
        "    \n" +
        "    // Raymarch Screen-Space Shadows using Elevation Map\n" +
        "    float shadow = 0.0;\n" +
        "    if (vType != TYPE_WATER) {\n" +
        "        vec2 samplePos = truePos.xy;\n" +
        "        float originX = 60.0;\n" +
        "        float originY = 60.0;\n" +
        "        float HEX_SIZE = 30.0;\n" +
        "        for (int i = 1; i <= 20; i++) {\n" +
        "            samplePos -= vec2(0.8, 0.6) * 5.0;\n" +
        "            float adjX = samplePos.x - originX;\n" +
        "            float adjY = samplePos.y - originY;\n" +
        "            float q = (2.0 / 3.0 * adjX) / HEX_SIZE;\n" +
        "            float r = (-1.0 / 3.0 * adjX + sqrt(3.0) / 3.0 * adjY) / HEX_SIZE;\n" +
        "            vec2 hex = vec2(floor(q + 0.5), floor(r + 0.5));\n" +
        "            if (hex.x < 0.0 || hex.y < 0.0 || hex.x >= 600.0 || hex.y >= 600.0) break;\n" +
        "            vec2 texCoords = vec2(hex.x / 600.0, hex.y / 600.0);\n" +
        "            float h = texture(elevationTex, texCoords).r * 255.0;\n" +
        "            float dist = float(i) * 5.0;\n" +
        "            if (h > vElevation + dist / 0.75) {\n" +
        "                shadow = 0.8;\n" + // Strong shadow but not pitch black
        "                break;\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "    \n" +
        "    float lighting = ambient + (1.0 - shadow) * diff * 0.6;\n" +
        "    \n" +
        "    color *= lighting;\n" +
        "    \n" +
        "    FragColor = vec4(color, 1.0);\n" +
        "}";
}
