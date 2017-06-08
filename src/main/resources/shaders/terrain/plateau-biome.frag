#version 330

uniform sampler2D regionBorderDistanceMask;
uniform sampler2D coastDistanceMask;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    float regionBorderDistance = texture(regionBorderDistanceMask, VertexIn.uv).r;
    if (regionBorderDistance > 0.9972) {
        colorOut = vec4(0.00001, 0.00001, 0.00001, 1.0);
    } else {
        float coastDistance = texture(coastDistanceMask, VertexIn.uv).r;
        if (coastDistance > 0.9987) {
            colorOut = vec4(0.00001, 0.00001, 0.00001, 1.0);
        } else {
            colorOut = vec4(1.0, 1.0, 1.0, 1.0);
        }
    }
}
