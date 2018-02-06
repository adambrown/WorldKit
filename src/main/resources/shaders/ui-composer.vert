#version 330

uniform mat4 modelViewProjectionMatrix;

in vec2 position;
in vec2 uv;

in vec4 instancePosition;

out VertexData {
    vec2 uv;
} VertexOut;

void main () {
    VertexOut.uv = uv;
    gl_Position = modelViewProjectionMatrix * vec4(instancePosition.xy + (position * instancePosition.zw), 0.0f, 1.0f);
}