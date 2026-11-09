#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec3 LightDir;
uniform vec3 AtmoColor;
uniform float AtmoStrength;
uniform float Mode;
uniform float Ambient;
uniform float Tilt;

in vec2 texCoord;
in vec4 vertexColor;
in vec3 bodyPos;
in vec3 viewVec;

out vec4 fragColor;

// Mode: 0 surface, 1 clouds, 2 star, 3 ring, 4 unlit (sky dome)
void main() {
    vec4 tex = texture(Sampler0, texCoord) * vertexColor * ColorModulator;
    vec3 v = normalize(viewVec);

    if (Mode > 3.5) {
        fragColor = vec4(tex.rgb, 1.0);
        return;
    }

    if (Mode > 2.5) {
        if (tex.a < 0.01) discard;
        // Planet shadow across the ring: the point is shadowed if the ray toward the
        // star passes within one planet radius of the centre.
        vec3 p = bodyPos;
        float along = dot(p, LightDir);
        vec3 perp = p - along * LightDir;
        float shadow = (along < 0.0 && dot(perp, perp) < 1.0) ? 0.12 : 1.0;
        vec3 n = vec3(0.0, cos(Tilt), sin(Tilt));
        float lit = Ambient + (1.0 - Ambient) * (0.35 + 0.65 * abs(dot(n, LightDir)));
        fragColor = vec4(tex.rgb * lit * shadow, tex.a);
        return;
    }

    vec3 n = normalize(bodyPos);
    float mu = max(dot(n, v), 0.0);

    if (Mode > 1.5) {
        // Stars: limb darkening, slightly over-bright core.
        float limb = 0.55 + 0.45 * pow(mu, 0.45);
        fragColor = vec4(min(tex.rgb * limb * 1.2, vec3(1.0)), 1.0);
        return;
    }

    float ndl = dot(n, LightDir);
    float day = smoothstep(-0.10, 0.22, ndl);
    float diffuse = max(ndl, 0.0);

    if (Mode > 0.5) {
        float shade = Ambient + (1.0 - Ambient) * diffuse;
        fragColor = vec4(tex.rgb * shade, tex.a * (0.15 + 0.85 * day));
        return;
    }

    // Surface: wrapped diffuse for a softer terminator, emissive lava/lights on the night side
    // (texture alpha below 1 marks emissive), and an atmospheric rim lit by the star.
    float wrapped = clamp((ndl + 0.08) / 1.08, 0.0, 1.0);
    vec3 lit = tex.rgb * (Ambient + (1.0 - Ambient) * wrapped);
    float emission = 1.0 - tex.a;
    lit += tex.rgb * emission * (1.0 - day * 0.55) * 1.3;
    float rim = pow(1.0 - mu, 3.0);
    lit += AtmoColor * rim * AtmoStrength * (0.1 + 0.9 * day);
    fragColor = vec4(lit, 1.0);
}
