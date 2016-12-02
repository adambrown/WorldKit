#version 410

uniform mat4 modelViewProjectionMatrix;
uniform mat4 modelViewMatrix;
uniform mat4 normalMatrix;

in vec3 position;
in vec3 normal;

out VertexData {
    vec3 position;
    vec3 mvmNormal;
    vec3 normal;
    vec3 eye;
} VertexOut;

void main () {
    vec4 pos4 = vec4(position, 1.0);
    vec4 mvmPos = modelViewMatrix * pos4;
    VertexOut.position = vec3(mvmPos);
    VertexOut.mvmNormal = vec3(modelViewMatrix * vec4(normal, 0.0));
    VertexOut.normal = vec3(normalize(normalMatrix * vec4(normal, 0.0)));
    VertexOut.eye = vec3(-mvmPos);
    gl_Position = modelViewProjectionMatrix * pos4;
}