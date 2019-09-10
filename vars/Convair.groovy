interface ConvairStage {
    Closure shouldRun
    Closure run
    String image
}


def call(Closure body) {
    def parameters = [env: env]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = parameters
    Map variables = parameters.variables ?: [:]
    variables.each {
        env[it.key] = it.value
    }
    body()


    Map<String, ConvairStage> selectedStages = parameters.selectedStages

    def nodeLabel = parameters.selectedAgent instanceof String ? parameters.selectedAgent : null
    def nodeParameters = [:]
    if(parameters.selectedAgent instanceof Closure){
        parameters.selectedAgent.delegate = nodeParameters
        parameters.selectedAgent.resolveStrategy = Closure.DELEGATE_FIRST
        parameters.selectedAgent()
        nodeLabel = nodeParameters.label ?: nodeLabel


    }
    node(nodeLabel) {


        stage("Initialize") {
            println parameters
            sh "env"
        }
        def scmVars
        stage("Checkout SCM") {
            scmVars = checkout scm
            scmVars.each {
                env[it.key] = it.value
            }
            println scmVars
        }
        def scriptClosure = owner
        try {

            selectedStages.each { myStage ->
                myStage.value.shouldRun.delegate = scriptClosure
                myStage.value.run.delegate = scriptClosure
                myStage.value.shouldRun.resolveStrategy = Closure.DELEGATE_FIRST
                myStage.value.run.resolveStrategy = Closure.DELEGATE_FIRST

                if (myStage.value.shouldRun()) {
                    def dockerImage = myStage.value.image ?: nodeParameters.image
                    if (dockerImage) {
                        docker.image(dockerImage).inside {
                            stage(myStage.key) {
                                myStage.value.run()
                            }
                        }
                    } else {
                        stage(myStage.key) {
                            myStage.value.run()
                        }
                    }
                } else {
                    println "Stage ${myStage.key} skipped"
                }
            }
            currentBuild.result = 'SUCCESS'
        } catch (Exception e) {
            e.printStackTrace()
            currentBuild.result = 'FAILURE'
        }

        if (currentBuild.result == "FAILURE") {
            if (parameters.onFailure && parameters.onFailure instanceof Closure) {
                stage("On Failure") {
                    parameters.onFailure.delegate = scriptClosure
                    parameters.onFailure.resolveStrategy = Closure.DELEGATE_FIRST
                    parameters.onFailure()
                }
            }
        }

        if (currentBuild.result == "SUCCESS") {
            if (parameters.onSuccess && parameters.onSuccess instanceof Closure) {
                stage("On Success") {
                    parameters.onSuccess.delegate = scriptClosure
                    parameters.onSuccess.resolveStrategy = Closure.DELEGATE_FIRST
                    parameters.onSuccess()
                }
            }
        }

        if (parameters.always && parameters.always instanceof Closure) {
            stage("Always") {
                parameters.always.delegate = scriptClosure
                parameters.always.resolveStrategy = Closure.DELEGATE_FIRST
                parameters.always()
            }
        }
    }
}
