#version 150

// Point sprites placed at their true direction in space, at the same compressed
// "sky distance" the planet renderer uses, so they sort and line up with planets.
// Positions are relative to the field's origin; Offset = origin - viewpoint.

in vec3 Position;
in vec2 UV0;      // corner sign * world radius of the glow
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Offset;
uniform float MinAngle;
uniform float MaxAngle;

out vec2 texCoord;
out vec4 vertexColor;

void main() {
    vec3 rel = Offset + Position;
    float dist = max(length(rel), 0.001);
    float sky = 10.0 + 8.0 * log(1.0 + dist / 20.0);
    vec3 centre = rel / dist * sky;

    vec2 corner = sign(UV0);
    float trueAngle = abs(UV0.x) / dist;
    float angle = clamp(trueAngle, MinAngle, MaxAngle);

    vec4 viewPos = ModelViewMat * vec4(centre, 1.0);
    viewPos.xy += corner * angle * sky;
    gl_Position = ProjMat * viewPos;

    texCoord = corner * 0.5 + 0.5;
    // Points enlarged to stay visible are dimmed instead, so distance still reads.
    float fade = clamp(trueAngle / MinAngle, 0.18, 1.0);
    vertexColor = vec4(Color.rgb, Color.a * fade);
}
