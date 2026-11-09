#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 BodyCenter;
uniform float BodyScale;
uniform float Spin;
uniform float Tilt;

out vec2 texCoord;
out vec4 vertexColor;
out vec3 bodyPos;
out vec3 viewVec;

vec3 rotateY(vec3 p, float a) {
    float c = cos(a), s = sin(a);
    return vec3(c * p.x + s * p.z, p.y, -s * p.x + c * p.z);
}

vec3 rotateX(vec3 p, float a) {
    float c = cos(a), s = sin(a);
    return vec3(p.x, c * p.y - s * p.z, s * p.y + c * p.z);
}

void main() {
    // Spin about the body's own axis, then tilt that axis. The mesh is a unit sphere
    // (or a ring in planet radii), so the rotated position doubles as the normal.
    vec3 p = rotateX(rotateY(Position, Spin), Tilt);
    vec3 world = BodyCenter + p * BodyScale;
    gl_Position = ProjMat * ModelViewMat * vec4(world, 1.0);
    texCoord = UV0;
    vertexColor = Color;
    bodyPos = p;
    viewVec = -world;
}
