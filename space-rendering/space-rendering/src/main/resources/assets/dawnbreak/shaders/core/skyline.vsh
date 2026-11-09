#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Offset;

out vec4 vertexColor;

void main() {
    vec3 rel = Offset + Position;
    float dist = max(length(rel), 0.001);
    float sky = 10.0 + 8.0 * log(1.0 + dist / 20.0);
    gl_Position = ProjMat * ModelViewMat * vec4(rel / dist * sky, 1.0);
    vertexColor = Color;
}
