#version 150

uniform vec3 LightDir;
uniform vec3 AtmoColor;
uniform float AtmoStrength;
uniform float ShellScale;

in vec2 texCoord;
in vec4 vertexColor;
in vec3 bodyPos;
in vec3 viewVec;

out vec4 fragColor;

void main() {
    // Distance (in planet radii) of the view ray from the planet centre: bright just past
    // the limb, fading out to the edge of the shell, faint over the disc itself.
    vec3 v = normalize(viewVec);
    vec3 p = bodyPos * ShellScale;
    float d = length(cross(p, v));
    float glow = d < 1.0 ? pow(d, 10.0) * 0.6
                         : pow(clamp(1.0 - (d - 1.0) / (ShellScale - 1.0), 0.0, 1.0), 2.2);
    float sun = dot(normalize(bodyPos), LightDir);
    float light = smoothstep(-0.35, 0.45, sun);
    // Sunset tint along the terminator.
    vec3 colour = mix(vec3(1.0, 0.5, 0.25), AtmoColor, smoothstep(-0.1, 0.35, sun));
    float a = glow * light * AtmoStrength;
    fragColor = vec4(colour * a, a);
}
