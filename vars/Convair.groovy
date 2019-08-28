interface ConvairStage {
    Closure shouldRun
    Closure run
}


def call(Closure body){
    def parameters = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = parameters
    parameters.env = env
    body()

    Map variables = parameters.variables ?: [:]
    variables.each {
        env[it.key] = it.value
    }

    Map<String, ConvairStage> selectedStages = parameters.selectedStages

    node(parameters.selectedAgent) {
        stage("Initialize"){
            println parameters
            sh "env"
        }
        def scmVars
        stage("Checkout SCM"){
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

                if(myStage.value.shouldRun()){
                    stage(myStage.key){
                        myStage.value.run()
                    }
                } else {
                    println "Stage ${myStage.key} skipped"
                }
            }
            currentBuild.result = 'SUCCESS'
        } catch (Exception e){
            e.printStackTrace()
            currentBuild.result = 'FAILURE'
        }

        if(currentBuild.result == "FAILURE"){
            if(parameters.onFailure && parameters.onFailure instanceof Closure){
                stage("On Failure"){
                    parameters.onFailure.delegate = scriptClosure
                    parameters.onFailure.resolveStrategy = Closure.DELEGATE_FIRST
                    parameters.onFailure()
                }
            }
        }

        if(currentBuild.result == "SUCCESS"){
            if(parameters.onSuccess && parameters.onSuccess instanceof Closure){
                stage("On Success"){
                    parameters.onSuccess.delegate = scriptClosure
                    parameters.onSuccess.resolveStrategy = Closure.DELEGATE_FIRST
                    parameters.onSuccess()
                }
            }
        }

        if(parameters.always && parameters.always instanceof Closure){
            stage("Always"){
                parameters.always.delegate = scriptClosure
                parameters.always.resolveStrategy = Closure.DELEGATE_FIRST
                parameters.always()
            }
        }
    }
}
