#version 410

uniform mat4 modelViewProjectionMatrix;
uniform mat4 modelViewMatrix;

in vec3 position;
in vec3 normal;

out VertexData {
    vec3 position;
    vec3 normal;
} VertexOut;

void main() {
    vec4 pos = vec4(position, 1.0);
    VertexOut.position = vec3(modelViewMatrix * pos);
    VertexOut.normal = vec3(modelViewMatrix * vec4(normal, 0.0));
    gl_Position = modelViewProjectionMatrix * pos;
}